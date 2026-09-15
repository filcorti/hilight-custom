package com.hilight.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Applies FOREGROUND rules: while a chosen app is on screen, HiLight holds that app's look.
 *
 * Uses UsageStatsManager event queries (needs Usage access, which the user grants in Settings)
 * because no non-privileged API reports the foreground package directly.
 */
class ForegroundWatcher : Service() {

    internal enum class SyncResult { STARTING_OR_RUNNING, STOPPED, FAILED }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val main = Handler(Looper.getMainLooper())
    private val store by lazy { Store.get(this) }
    private var lastPkg: String? = null
    private val foreground = ForegroundAppTracker()
    private var queriedThroughMs = Long.MIN_VALUE
    @Volatile private var forceRefresh = true
    @Volatile private var plan = ForegroundWatchPlan(false, false)
    private lateinit var faceDownTracker: FaceDownSensorTracker

    private val usageStatsManager by lazy { getSystemService(UsageStatsManager::class.java) }

    /**
     * Set on the main thread when the service is going away.
     *
     * The poll below queries UsageStats over binder, so a tick can still be in flight when the
     * service is destroyed — `removeCallbacksAndMessages` cannot recall one that already started.
     * Checking this on the main thread, where the override is also cleared, keeps a late tick from
     * re-applying an override with no watcher left alive to ever clear it.
     */
    @Volatile private var stopped = false

    private val tick = object : Runnable {
        override fun run() {
            if (!plan.trackForegroundApps) return
            val pkg = currentForegroundPackage()
            if (forceRefresh || pkg != lastPkg) {
                forceRefresh = false
                lastPkg = pkg
                // Store is a main-thread object: every other caller mutates it from there, and its
                // override field and file writes are not synchronized.
                main.post {
                    if (!stopped) {
                        val rule = pkg?.let { store.ruleFor(it, Trigger.FOREGROUND) }
                        store.setForegroundOverride(pkg?.takeIf { rule != null }, rule)
                    }
                }
            }
            if (plan.trackForegroundApps) handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("fg-watch").also { it.start() }
        handler = Handler(thread.looper)
        faceDownTracker = FaceDownSensorTracker(this, handler) { state, sampleElapsedMs ->
            // Sensor callbacks can already be queued while the service is tearing down. Mirror the
            // foreground-app path's stopped guard so a retired watcher cannot publish late state.
            main.post {
                if (!stopped) store.updateFaceDownSensorState(state, sampleElapsedMs)
            }
        }
        startForeground(NOTIFICATION_ID, notification(plan))
    }

    override fun onDestroy() {
        stopped = true
        // The listener is registered on this looper, so unregister it before asking the thread to quit.
        faceDownTracker.stop()
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        main.post {
            store.setForegroundOverride(null, null)
            store.updateFaceDownSensorState(FaceDownState.INACTIVE, 0L)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyPlan(
            intent?.let(::planFromIntent) ?: store.foregroundWatchPlan()
        )
        return START_STICKY
    }

    private fun applyPlan(next: ForegroundWatchPlan) {
        val prior = plan
        plan = next
        startForeground(NOTIFICATION_ID, notification(next))

        // syncRunning also calls start when the service already exists. Re-evaluate the same package
        // so enabling or editing its rule cannot be ignored just because the app did not change.
        forceRefresh = true
        handler.removeCallbacks(tick)
        if (next.trackForegroundApps) {
            handler.post(tick)
        } else if (prior.trackForegroundApps) {
            foreground.clear()
            lastPkg = null
            queriedThroughMs = Long.MIN_VALUE
            main.post { if (!stopped) store.setForegroundOverride(null, null) }
        }

        if (prior.trackFaceDown != next.trackFaceDown) {
            handler.post {
                if (stopped) return@post
                if (plan.trackFaceDown) {
                    faceDownTracker.start()
                    // Close the tiny race where teardown began after the check above but while
                    // SensorManager was registering the listener.
                    if (stopped) faceDownTracker.stop()
                } else {
                    faceDownTracker.stop()
                    main.post {
                        if (!stopped) {
                            store.updateFaceDownSensorState(FaceDownState.INACTIVE, 0L)
                        }
                    }
                }
            }
        }
    }

    private fun currentForegroundPackage(): String? {
        if (!hasUsageAccess(this)) {
            foreground.clear()
            queriedThroughMs = Long.MIN_VALUE
            return null
        }
        val usm = usageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val bootWallTime = now - SystemClock.elapsedRealtime()
        val begin = if (queriedThroughMs == Long.MIN_VALUE) {
            maxOf(bootWallTime, now - BOOTSTRAP_LOOKBACK_MS)
        } else {
            maxOf(bootWallTime, queriedThroughMs - QUERY_OVERLAP_MS)
        }
        val events = usm.queryEvents(begin, now)
        val e = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val lifecycle = when (e.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundLifecycle.RESUMED
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundLifecycle.PAUSED
                else -> null
            }
            if (lifecycle != null) foreground.accept(e.packageName, e.className, lifecycle)
        }
        queriedThroughMs = now
        return foreground.currentPackage()
    }

    private fun notification(plan: ForegroundWatchPlan): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        // A Service has no composition, so getString rather than stringResource. CHANNEL is the id
        // the notification is registered under and is never read by a person; the name beside it is
        // the label shown in the system notification settings, so that one is translated.
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.service_watcher_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.service_watcher_title))
            .setContentText(
                getString(
                    when {
                        plan.trackForegroundApps && plan.trackFaceDown ->
                            R.string.service_watcher_text_both
                        plan.trackFaceDown -> R.string.service_watcher_text_face_down
                        else -> R.string.service_watcher_text
                    }
                )
            )
            .setSmallIcon(R.drawable.hilight_logo)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "fg_watch"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_FOREGROUND = "trackForegroundApps"
        private const val EXTRA_FACE_DOWN = "trackFaceDown"
        private const val POLL_MS = 1000L
        private const val QUERY_OVERLAP_MS = 2_000L
        private const val BOOTSTRAP_LOOKBACK_MS = 24 * 60 * 60_000L

        /** Starts or stops the watcher to match one authoritative work plan. */
        internal fun syncRunning(ctx: Context, plan: ForegroundWatchPlan): SyncResult {
            val intent = Intent(ctx, ForegroundWatcher::class.java)
                .putExtra(EXTRA_FOREGROUND, plan.trackForegroundApps)
                .putExtra(EXTRA_FACE_DOWN, plan.trackFaceDown)
            return runCatching {
                if (plan.shouldRun) {
                    ctx.startForegroundService(intent)
                    SyncResult.STARTING_OR_RUNNING
                } else {
                    ctx.stopService(intent)
                    SyncResult.STOPPED
                }
            }.onFailure {
                Log.w("HiLightForeground", "could not update foreground watcher", it)
            }.getOrDefault(SyncResult.FAILED)
        }

        private fun planFromIntent(intent: Intent): ForegroundWatchPlan = ForegroundWatchPlan(
            trackForegroundApps = intent.getBooleanExtra(EXTRA_FOREGROUND, false),
            trackFaceDown = intent.getBooleanExtra(EXTRA_FACE_DOWN, false),
        )

        fun hasFaceDownSensor(ctx: Context): Boolean = FaceDownSensorTracker.hasSensor(ctx)

        fun hasUsageAccess(ctx: Context): Boolean {
            val appOps = ctx.getSystemService(AppOpsManager::class.java) ?: return false
            return appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }
    }
}
