package com.hilight.studio

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns notifications from chosen apps into HiLight alerts.
 *
 * Everything a rule could match on is pulled out of the notification once, by [NotificationPeek], and
 * then handed to [Store]: the chat is remembered so the rule picker can offer it later, the peek is
 * kept for the inspector, and only after that is a rule looked for. The noting deliberately happens
 * before every reason to stop below, because an app with no rule yet is exactly the one the user is
 * about to write a rule for.
 */
class NotificationTrigger : NotificationListenerService() {

    private val store by lazy { Store.get(this) }
    private val main = Handler(Looper.getMainLooper())
    private val incomingCalls = mutableMapOf<String, Long>()
    private val conversationKnocks = object : LinkedHashMap<String, Pair<Long, Int>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Int>>): Boolean =
            size > MAX_TRACKED
    }
    private val KNOCK_WINDOW_MS = 15_000L
    private var connected = false
    private var observationScope: CoroutineScope? = null
    private var signalOwner: String? = null
    private var breatheJob: kotlinx.coroutines.Job? = null
    private val activeNotifKeys = mutableSetOf<String>()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    main.removeCallbacks(tick)
                    scheduleTick()
                    stopBreatheReminder()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    startBreatheReminderIfEligible()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(unlockReceiver, filter)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Android reconnects an approved notification listener after boot. Touching Store here
        // restores persisted While open rules without requiring the user to open HiLight Studio or
        // wait for an unrelated notification first.
        store.syncForegroundWatcher()
        connected = true
        store.deviceSignals.onInterruptionFilterChanged(currentInterruptionFilter)
        observationScope?.cancel()
        observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch { store.deviceSignals.settings.collect { reconcileSignals() } }
            scope.launch { store.rules.collect { reconcileSignals() } }
            scope.launch {
                store.enabled.collect { enabled ->
                    if (!enabled) clearSignals() else reconcileSignals()
                }
            }
        }
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
        store.deviceSignals.onInterruptionFilterChanged(interruptionFilter)
        reconcileSignals()
    }

    /**
     * The newest message stamp already acted on, per notification key.
     *
     * Messaging apps re-post the same notification for anything that changes the chat — a read
     * receipt, a typing indicator, another message in a different chat inside the same bundle — and
     * every re-post arrives here as a fresh callback. Without this the same sentence flashes the array
     * several times over.
     *
     * Access-ordered and self-trimming, so when it fills the entries it drops are the chats that have
     * been quiet the longest. Keys for notifications the user dismisses are removed in
     * [onNotificationRemoved]; the bound is there for the ones that went away while the listener was
     * not bound to see it.
     */
    private val handled = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
            size > MAX_TRACKED
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // This is a system callback, and a listener that throws is a listener the framework may stop
        // trusting — which would take every rule with it. One catch around the whole path is worth
        // more than hoping each step in it behaves.
        runCatching { handlePosted(sbn) }
            .onFailure { Log.w(TAG, "could not handle notification", it) }
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        Log.d(TAG, "posted by ${sbn.packageName}")
        // HiLight's own foreground-watcher notification, which would otherwise light the array through
        // the catch-all rule every time the watcher restarted.
        if (isOwnStatusNotification(sbn)) return

        activeNotifKeys.add(sbn.key)
        if (!screenOn() && store.breatheReminderEnabled.value) {
            startBreatheReminderIfEligible()
        }

        val info = readMessage(sbn)

        // Se NON è una chiamata gestita da una nostra regola WhatsApp/app, lascia il comportamento standard delle chiamate di sistema
        if (!info.isCall) {
            updateIncomingCall(sbn)
            if (store.deviceSignals.settings.value.callsEnabled && incoming(sbn)) return
        }
        store.notePeek(info)
        store.noteConversation(info)

        if (info.isGroupSummary) return                 // the app's own "3 new messages" wrapper
        if (info.isOngoing) return                      // media/progress notifications repeat a lot
        if (!isNewMessage(info)) {
            // The stamps go in the line because they are the only way to tell a genuine duplicate
            // (identical stamp) from a message that arrived carrying an older one — a resend, or a
            // sender whose clock runs behind, both of which look like a re-post from here.
            Log.i(
                TAG,
                "re-post from ${info.pkg}: stamp=${ConversationMatch.stampOf(info)} " +
                        "not newer than ${lastStampFor(info.notifKey)}",
            )
            return
        }

        val rule = store.ruleForMessage(info) ?: return
        if (rule.keyword.isNotBlank() && !matchesKeyword(info, rule.keyword)) {
            return
        }

        // These two guards silence the flash, but the rule did match, and the rules screen shows
        // exactly that: "last matched". Returning before recording it would leave a working rule
        // reading "not matched yet" for anyone who keeps Do Not Disturb on or asked for screen-off
        // flashes only — indistinguishable from a rule that has never matched anything. The guards
        // inside fireAlert record the match for the same reason.
        if (rule.onlyWhenScreenOff && screenOn()) {
            Log.i(TAG, "matched ${info.pkg} but the rule only flashes with the screen off")
            store.noteRuleFired(rule, info)
            return
        }
        if (rule.onlyWhenFaceDown && !store.isFaceDownNow()) {
            Log.i(TAG, "matched ${info.pkg} but the rule only flashes while face down")
            store.noteRuleFired(rule, info)
            return
        }
        if (store.respectDnd.value && inDoNotDisturb()) {
            Log.i(TAG, "matched ${info.pkg} but suppressed by Do Not Disturb")
            store.noteRuleFired(rule, info)
            return
        }
        if (store.isScheduledQuietActive()) {
            Log.i(TAG, "matched ${info.pkg} but suppressed by scheduled quiet hours")
            store.noteRuleFired(rule, info)
            return
        }

        // How it matched, never what the message said and never who sent it. A conversation rule's
        // label is a contact's name, and CONTRIBUTING asks users to scrub personal data out of logs
        // they share — so the line says whether a chat rule or an app rule won, which is enough to
        // answer "why did that one fire and not my other one" without naming anybody.
        val how = ConversationMatch.strength(rule, info)
            ?: if (rule.isCatchAll) MatchStrength.CATCH_ALL else MatchStrength.APP
        val scope = if (rule.isConversationRule) "chat" else "app"

        // --- GESTIONE DOPPIA BUSSATA ---
        val effectiveRule = if (rule.knockEnabled && !info.isCall) {
            val chatKey = rule.conversationKey ?: rule.conversationName ?: info.sender.orEmpty()
            val now = SystemClock.elapsedRealtime()

            val knockLevel = synchronized(conversationKnocks) {
                val previous = conversationKnocks[chatKey]
                val level = if (previous != null && (now - previous.first) <= KNOCK_WINDOW_MS) {
                    (previous.second + 1).coerceAtMost(3)
                } else {
                    1
                }
                conversationKnocks[chatKey] = Pair(now, level)
                level
            }

            when (knockLevel) {
                2 -> {
                    // 2° messaggio entro 15s: doppio battito rapido
                    rule.copy(
                        pattern = Pattern.HEARTBEAT,
                        speedMs = 600,
                        durationMs = 4_000,
                    )
                }
                3 -> {
                    // 3° messaggio o oltre: effetto dinamico a rimbalzo
                    rule.copy(
                        pattern = Pattern.BOUNCE,
                        speedMs = 400,
                        durationMs = 6_000,
                    )
                }
                else -> rule // 1° messaggio: usa il colore e pattern base della regola
            }
        } else {
            rule
        }

        Log.i(TAG, "alert for ${info.pkg} rule=$scope match=$how pattern=${effectiveRule.pattern.key} isCall=${info.isCall}")
        store.fireAlert(effectiveRule, owner = "notification:${sbn.key}")
        store.noteRuleFired(effectiveRule, info)
    }

    /**
     * Forgets a dismissed notification's stamp.
     *
     * The key is derived exactly the way it was on the way in, so the two sides cannot drift apart if
     * the peek ever stops using the framework's own key. Without this a chat that is dismissed and
     * then says the same thing again — a resend, which carries the original message's older stamp —
     * would be taken for a re-post and ignored.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatching {
            val key = sbn.key
            if (!key.isNullOrEmpty()) {
                synchronized(handled) { handled.remove(key) }
                activeNotifKeys.remove(key)
            }
            incomingCalls.remove(sbn.key)
            store.cancelOwnedAlert("notification:${sbn.key}")
            store.cancelOwnedAlert("call:${sbn.key}")
            if (activeNotifKeys.isEmpty()) stopBreatheReminder()
            reconcileSignals()
        }.onFailure { Log.w(TAG, "could not handle removal", it) }
    }

    private fun startBreatheReminderIfEligible() {
        breatheJob?.cancel()
        if (!store.breatheReminderEnabled.value) return
        if (store.isScheduledQuietActive()) return
        if (activeNotifKeys.isEmpty()) return
        if (!screenOn()) {
            val intervalSec = store.breatheReminderIntervalSec.value.coerceAtLeast(5)
            breatheJob = observationScope?.launch {
                while (true) {
                    delay(intervalSec * 1000L)
                    if (screenOn() || activeNotifKeys.isEmpty()) break
                    store.showDeviceSignal(
                        "breathe_reminder",
                        Ambient(
                            pattern = Pattern.BREATHE,
                            color = CalibratedLedColors.EMERALD_GREEN,
                            speedMs = 1000,
                            speedMultiplier = 1.0f,
                            maxBrightness = 0.15f,
                            brightness = 0.15f
                        ),
                        2000
                    )
                }
            }
        }
    }

    private fun stopBreatheReminder() {
        breatheJob?.cancel()
        breatheJob = null
        store.cancelOwnedAlert("breathe_reminder")
    }

    /**
     * Everything the de-duplication map holds describes notifications that were on screen while this
     * listener was bound. After a rebind the shade has moved on without us, so the old stamps can only
     * be misleading.
     */
    override fun onListenerDisconnected() {
        synchronized(handled) { handled.clear() }
        connected = false
        observationScope?.cancel()
        observationScope = null
        clearSignals()
        store.deviceSignals.onInterruptionFilterChanged(INTERRUPTION_FILTER_UNKNOWN)
    }

    override fun onDestroy() {
        stopBreatheReminder()
        onListenerDisconnected()
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    private fun readMessage(sbn: StatusBarNotification): MessageInfo {
        val ranking = Ranking()
        val ranked = currentRanking.getRanking(sbn.key, ranking)
        val channel = if (ranked) ranking.channel else null
        val silent = if (ranked) {
            isSilentNotification(ranking.importance, channel == null || channel.sound != null, channel?.shouldVibrate() == true)
        } else false
        return NotificationPeek.read(sbn).copy(isSilent = silent)
    }

    private fun incoming(sbn: StatusBarNotification): Boolean = runCatching {
        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                isIncomingCallType(sbn.notification.extras.getInt(Notification.EXTRA_CALL_TYPE, 0))
    }.getOrDefault(false)

    private fun isOwnStatusNotification(sbn: StatusBarNotification): Boolean =
        sbn.packageName == packageName && sbn.notification.channelId in
                setOf("fg_watch", SHIZUKU_RECOVERY_CHANNEL)

    private fun updateIncomingCall(sbn: StatusBarNotification) {
        if (store.deviceSignals.settings.value.callsEnabled && incoming(sbn)) {
            incomingCalls[sbn.key] = sbn.postTime
        } else if (incomingCalls.remove(sbn.key) != null) store.cancelOwnedAlert("call:${sbn.key}")
        if (incomingCalls.isNotEmpty()) scheduleTick(immediate = true)
    }

    private val tick = Runnable { reconcileSignals() }

    private fun scheduleTick(immediate: Boolean = false) {
        main.removeCallbacks(tick)
        if (connected && store.enabled.value && incomingCalls.isNotEmpty()) {
            val delay = if (immediate) 0L else 2_000L
            main.postDelayed(tick, delay)
        }
    }

    private fun clearSignals() {
        main.removeCallbacks(tick)
        incomingCalls.clear()
        signalOwner?.let(store::cancelOwnedAlert)
        signalOwner = null
    }

    private fun reconcileSignals() {
        runCatching { reconcileSignalState() }.onFailure {
            clearSignals()
            Log.w(TAG, "could not refresh notification signals", it)
        }
    }

    private fun reconcileSignalState() {
        if (!connected || !store.enabled.value) { clearSignals(); return }
        val settings = store.deviceSignals.settings.value

        // If calls are disabled via settings, clear out active calls
        if (!settings.callsEnabled && incomingCalls.isNotEmpty()) {
            incomingCalls.keys.forEach { store.cancelOwnedAlert("call:$it") }
            incomingCalls.clear()
        }

        val newestCallKey = incomingCalls.maxByOrNull { it.value }?.key
        val owner = newestCallKey?.let { "call:$it" }

        if (signalOwner != owner) signalOwner?.let(store::cancelOwnedAlert)
        signalOwner = owner
        if (owner != null) {
            store.showDeviceSignal(owner, Ambient(pattern = Pattern.PULSE, color = settings.callColor, speedMs = 1000), 2500)
        }

        scheduleTick()
    }

    /** The stamp last acted on for [notifKey], for the log line that explains a discarded re-post. */
    private fun lastStampFor(notifKey: String): Long? =
        synchronized(handled) { handled[notifKey] }

    private fun isNewMessage(info: MessageInfo): Boolean {
        if (info.notifKey.isEmpty()) return true
        synchronized(handled) {
            if (!ConversationMatch.isNewer(info, handled[info.notifKey])) return false
            handled[info.notifKey] = ConversationMatch.stampOf(info)
            return true
        }
    }

    private fun inDoNotDisturb(): Boolean =
        currentInterruptionFilter.let {
            it == INTERRUPTION_FILTER_PRIORITY ||
                    it == INTERRUPTION_FILTER_ALARMS ||
                    it == INTERRUPTION_FILTER_NONE
        }

    private fun matchesKeyword(info: MessageInfo, keyword: String): Boolean {
        val haystack = buildString {
            append(info.title.orEmpty())
            append(' ')
            append(info.text.orEmpty())
            append(' ')
            append(info.sender.orEmpty())
            append(' ')
            append(info.conversationTitle.orEmpty())
        }
        return haystack.contains(keyword.trim(), ignoreCase = true)
    }

    private fun screenOn(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private companion object {
        const val TAG = "HiLightNotif"
        const val MAX_TRACKED = 200
    }
}

private fun isSilentNotification(importance: Int, hasSound: Boolean, shouldVibrate: Boolean): Boolean {
    if (importance < NotificationManager.IMPORTANCE_DEFAULT) return true
    return !hasSound && !shouldVibrate
}

private fun isIncomingCallType(callType: Int): Boolean {
    return callType == 1
}