package com.hilight.studio

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Reads the fields a rule needs out of a live notification.
 *
 * This is the Android half of the conversation matcher. Everything here touches the framework, and
 * everything it produces is the plain [MessageInfo] that Conversation.kt can reason about with no
 * device attached. The split is deliberate: the interesting failures are all about which name a
 * particular app happens to put where, and those are only cheap to pin down if the matching ladder
 * can be driven from a unit test.
 *
 * The whole file is written to survive hostile input. It runs inside [NotificationTrigger], a
 * [android.service.notification.NotificationListenerService], on notifications from every app on
 * the phone. A notification's `extras` is a [Bundle] authored by another process and can carry a
 * Parcelable this process cannot even load, so reading it throws far more readily than the
 * signatures suggest. An exception there is not a caught error somewhere in the UI — it is a dead
 * listener service and a light that quietly stops working until the user notices and re-grants the
 * permission. So every framework read sits inside a `runCatching`, and [read] never throws.
 */
object NotificationPeek {

    private const val TAG = "HiLightPeek"

    /**
     * Everything the matcher needs from [sbn], and never an exception.
     *
     * Worst case — unreadable extras, or a notification whose parcel this process cannot open — the
     * result still carries `pkg`, `notifKey` and `postTimeMs`. That is enough for a plain per-app
     * rule to keep working, and enough for the diagnostics screen to say that something arrived and
     * was not understood, which is a far more useful answer than nothing at all.
     */
    fun read(sbn: StatusBarNotification): MessageInfo {
        // These three come off the StatusBarNotification itself rather than out of the foreign
        // parcel, so they are the part that survives when the rest of the read falls over. Take
        // them first and keep them separate.
        val bare = MessageInfo(
            pkg = runCatching { sbn.packageName }.getOrNull().orEmpty(),
            notifKey = runCatching { sbn.key }.getOrNull().orEmpty(),
            postTimeMs = runCatching { sbn.postTime }.getOrDefault(0L),
        )
        // A failed read is flagged rather than left looking like a notification that simply named
        // nobody. The two are indistinguishable otherwise, and the advice for each is opposite: one
        // means "write a rule for the whole app instead", the other means "this is a bug, report it".
        val notification = runCatching { sbn.notification }.getOrNull()
            ?: return bare.copy(readFailed = true)
        return runCatching { readFrom(bare, notification) }.getOrElse {
            Log.w(TAG, "unreadable notification from ${bare.pkg}", it)
            bare.copy(readFailed = true)
        }
    }

    // ------------------------------------------------------------------ the read itself

    private fun readFrom(bare: MessageInfo, n: Notification): MessageInfo {
        val extras = runCatching { n.extras }.getOrNull()

        // A bundling app posts a summary alongside the real notifications, repeating whatever the
        // children already said. Firing on it would flash every colour twice — and worse, the
        // summary carries the *bundle's* text rather than one chat's, so a rule naming a single
        // contact would light up for anybody in the bundle. The matcher drops these, but only if
        // it is told about them.
        val isSummary = runCatching { n.flags and Notification.FLAG_GROUP_SUMMARY != 0 }
            .getOrDefault(false)

        // 1. Rileva se è una chiamata
        val isCall = runCatching {
            val isCallCategory = n.category == Notification.CATEGORY_CALL
            val isCallStyle = extras?.charSeq(Notification.EXTRA_TEMPLATE)?.contains("CallStyle") == true
            val hasCallExtra = extras?.containsKey("android.callType") == true ||
                    extras?.containsKey(Notification.EXTRA_CALL_TYPE) == true
            isCallCategory || isCallStyle || hasCallExtra
        }.getOrDefault(false)

        // 2. Ongoing: se è una chiamata NON considerarla ongoing, così il listener non la scarta
        val ongoing = runCatching {
            n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        }.getOrDefault(false) && !isCall

        // shortcutId has been on Notification since API 26...
        val shortcutId = runCatching { n.shortcutId }.getOrNull().clean()
        // Title and text are filled in whatever the template turns out to be. For a MessagingStyle
        // notification the platform mirrors the newest message into them, so they are never the
        // matcher's first choice — but they are what the "why didn't my rule fire?" screen shows,
        // and for the apps that never adopted MessagingStyle they are the only thing there is.
        val title = extras.charSeq(Notification.EXTRA_TITLE).clean()
        val text = extras.charSeq(Notification.EXTRA_TEXT).clean()
            ?: extras.charSeq(Notification.EXTRA_BIG_TEXT).clean()

        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()

        val chat = style?.let { readStyle(it, extras) } ?: readLegacyMessages(extras)

        return bare.copy(
            shortcutId = shortcutId,
            sender = chat?.sender,
            conversationTitle = chat?.conversationTitle,
            title = title,
            text = text,
            isGroupSummary = isSummary,
            isOngoing = ongoing,
            isMessagingStyle = style != null,
            messageStampMs = chat?.stampMs ?: 0L,
            isCall = isCall,
        )
    }

    /** The conversation fields, whichever of the two paths managed to find them. */
    private data class Chat(
        val sender: String?,
        val conversationTitle: String?,
        val stampMs: Long,
    )

    // ------------------------------------------------------------------ MessagingStyle

    private fun readStyle(style: NotificationCompat.MessagingStyle, extras: Bundle?): Chat {
        val messages = runCatching { style.messages }.getOrNull().orEmpty()
        val newest = newestBy(messages) { stampOf(it) }
        val person = newest?.let { runCatching { it.person }.getOrNull() }
        val styleTitle = runCatching { style.conversationTitle }.getOrNull()
        return Chat(
            // In a group this is the speaker, not the group — which is what a rule naming a person
            // needs, and why the group's own name is kept well apart from it.
            sender = pickName(
                runCatching { person?.name }.getOrNull(),
                runCatching { person?.uri }.getOrNull(),
            ),
            conversationTitle = groupTitle(styleTitle, extras),
            // Messaging apps re-post the same notification every time anything about the
            // conversation changes: a read receipt, a typing indicator, another message in a
            // different chat inside the same bundle. Without the newest message's own timestamp
            // there is no telling a genuinely new message from one of those, and a single chat
            // then re-flashes on every one of them.
            stampMs = newest?.let { stampOf(it) } ?: 0L,
        )
    }

    private fun stampOf(message: NotificationCompat.MessagingStyle.Message): Long =
        runCatching { message.timestamp }.getOrDefault(0L)

    // ------------------------------------------------------------------ legacy extras

    /** Bundle keys the platform uses for each entry of `EXTRA_MESSAGES`. */
    private const val KEY_TEXT = "text"
    private const val KEY_TIME = "time"
    private const val KEY_SENDER = "sender"
    private const val KEY_SENDER_PERSON = "sender_person"

    /**
     * Second attempt at the conversation, straight out of `EXTRA_MESSAGES`.
     *
     * `extractMessagingStyleFromNotification` recognises a notification by its template and by the
     * two "who am I" extras, so an app that fills the message extras by hand, or that builds a
     * plain BigTextStyle and attaches the messages alongside it, comes back null with the data
     * sitting right there in the bundle. Slack and Discord are the ones we know reach this far.
     *
     * Best effort by design: it reads the keys the platform documents and gives up quietly on
     * anything else, because a wrong sender is worse than no sender.
     */
    private fun readLegacyMessages(extras: Bundle?): Chat? {
        val messages = legacyMessageBundles(extras)
        if (messages.isEmpty()) return null
        val newest = newestBy(messages) { it.longOrZero(KEY_TIME) } ?: return null
        // The text is not carried into MessageInfo — EXTRA_TEXT already holds it for every app seen
        // so far — but its absence is how the platform's own reader decides a bundle is not a
        // message at all, so it is worth the same check here.
        if (newest.charSeq(KEY_TEXT) == null && newest.charSeq(KEY_SENDER) == null) return null
        return Chat(
            sender = newest.charSeq(KEY_SENDER).clean() ?: legacyPersonName(newest),
            conversationTitle = groupTitle(null, extras),
            stampMs = newest.longOrZero(KEY_TIME),
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyMessageBundles(extras: Bundle?): List<Bundle> = runCatching {
        // The typed getParcelableArray overload is the one that is not deprecated, but it hands
        // back null when the parcel was written as a plain Parcelable[] — which is exactly how the
        // platform writes EXTRA_MESSAGES. androidx suppresses this same warning in its own reader
        // for this same reason, and losing every message to a tidier call is a poor trade.
        extras?.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.filterIsInstance<Bundle>()
            .orEmpty()
    }.getOrDefault(emptyList())

    /**
     * The sender of a legacy message bundle when it was stored as a Person rather than a name.
     *
     * The framework's own builder writes the plain `sender` string only for the deprecated
     * CharSequence overload; anything built with a Person lands under `sender_person` instead, so
     * this is the common case rather than the exotic one.
     */
    @Suppress("DEPRECATION")
    private fun legacyPersonName(message: Bundle): String? {
        // Read it as a bare Parcelable and cast here rather than letting getParcelable's own type
        // parameter do it: that cast is erased, so a bundle holding something else would sail
        // straight through and throw later, somewhere with no context left.
        val raw = runCatching { message.getParcelable<Parcelable>(KEY_SENDER_PERSON) }.getOrNull()
        val person = raw as? android.app.Person ?: return null
        return pickName(
            runCatching { person.name }.getOrNull(),
            runCatching { person.uri }.getOrNull(),
        )
    }

    // ------------------------------------------------------------------ group or one-to-one

    /**
     * The group's name, and null for a one-to-one chat.
     *
     * [MessageInfo.isGroup] is "conversationTitle is not blank", so returning a title for a
     * one-to-one chat quietly reclassifies the chat — and the obvious implementation does exactly
     * that. `NotificationCompat` writes the conversation title into `android.hiddenConversationTitle`
     * for *every* MessagingStyle, group or not, and `extractMessagingStyleFromNotification` falls
     * back to that hidden extra whenever the visible `EXTRA_CONVERSATION_TITLE` is missing.
     * Trusting [styleTitle] on its own therefore marks a good number of one-to-one chats as groups,
     * and a chat wrongly seen as a group stops "green when Sujay messages" firing at all unless
     * that rule happened to opt into groups.
     *
     * So the group question is settled first, and only then is a title returned. The answer is
     * `EXTRA_IS_GROUP_CONVERSATION` wherever the app set it, that being the one field which means
     * precisely this and nothing else. Where it is absent the visible conversation title stands in,
     * which is the pre-Android-9 convention for "this is a group".
     */
    private fun groupTitle(styleTitle: CharSequence?, extras: Bundle?): String? {
        val visible = extras.charSeq(Notification.EXTRA_CONVERSATION_TITLE).clean()
        val declared = extras.boolOrNull(Notification.EXTRA_IS_GROUP_CONVERSATION)
        if (declared == false || (declared == null && visible == null)) return null
        return visible ?: styleTitle.clean()
    }

    // ------------------------------------------------------------------ small mercies

    /**
     * The newest entry in [items] by [stamp], tolerating either ordering.
     *
     * Nearly every app appends oldest-first, so taking the last entry would usually do — but not
     * always, and a rule that lights for the wrong speaker is worse than one that stays dark.
     * Comparing stamps is therefore the primary route, with position as the fallback for the apps
     * that leave every timestamp at zero. Ties go to the later entry, because two messages sharing
     * a millisecond were still written in order.
     */
    private fun <T> newestBy(items: List<T>, stamp: (T) -> Long): T? {
        var best: T? = null
        var bestStamp = Long.MIN_VALUE
        for (item in items) {
            val s = stamp(item)
            if (s >= bestStamp) {
                best = item
                bestStamp = s
            }
        }
        return if (bestStamp > 0L) best else items.lastOrNull()
    }

    /**
     * A person's display name, or null when all the app offered was a machine identifier.
     *
     * The name is what the user recognises and what the contact picker produces on the other side
     * of the match, so anything else is a near-certain miss. Some apps do leave the name empty and
     * fill only the uri, which makes it worth a look — but a Person uri is usually
     * `tel:+441632960123` or `content://com.android.contacts/...`, and offering that in the rule
     * picker, or remembering it as a conversation name, is worse than admitting we do not know who
     * sent this.
     */
    private fun pickName(name: CharSequence?, uri: String?): String? {
        name.clean()?.let { return it }
        val handle = uri.clean() ?: return null
        return handle.takeUnless { isMachineId(it) }
    }

    /** The uri shapes seen in practice when an app puts an identifier where a name should be. */
    private val ID_SCHEMES = listOf(
        "tel:", "sms:", "smsto:", "mms:", "mmsto:", "mailto:", "content:", "im:", "imto:",
    )

    private fun isMachineId(s: String): Boolean =
        s.contains("://") || ID_SCHEMES.any { s.startsWith(it, ignoreCase = true) }

    /**
     * Blank is the same as absent.
     *
     * The matcher normalises both away, but the diagnostics screen prints these raw, and an empty
     * string there reads as "the app told us the sender is nothing" rather than "the app told us
     * nothing", which is the opposite of helpful.
     */
    private fun CharSequence?.clean(): String? =
        this?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun Bundle?.charSeq(key: String): CharSequence? =
        runCatching { this?.getCharSequence(key) }.getOrNull()

    private fun Bundle.longOrZero(key: String): Long =
        runCatching { getLong(key, 0L) }.getOrDefault(0L)

    /** Null means the app never set the flag, a different answer from setting it to false. */
    private fun Bundle?.boolOrNull(key: String): Boolean? =
        runCatching { if (this != null && containsKey(key)) getBoolean(key) else null }.getOrNull()
}
