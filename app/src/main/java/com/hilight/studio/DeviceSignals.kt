package com.hilight.studio

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional signals, independent of existing app rules and disabled for existing installations. */
/** Optional signals, independent of existing app rules and disabled for existing installations. */
data class DeviceSignalSettings(
    val chargingEnabled: Boolean = false,
    val chargingColor: Int = 0xFF2979FF.toInt(),
    val chargedColor: Int = 0xFF00E676.toInt(),
    val fullPercent: Int = 100,
    val faceDownBatteryEnabled: Boolean = true,
    val callsEnabled: Boolean = false,
    val callColor: Int = 0xFF00E676.toInt(),
    val callPattern: Pattern = Pattern.BLINK,
)

internal fun chargingSignalLook(settings: DeviceSignalSettings, percent: Int): Ambient = Ambient(
    pattern = if (percent >= settings.fullPercent) Pattern.SOLID else Pattern.BLINK,
    color = if (percent >= settings.fullPercent) settings.chargedColor else settings.chargingColor,
    speedMs = 600,
)

/** Without a listener observation, Respect DND must not become permission to light up. */
internal fun dndBlocksSignals(filter: Int?): Boolean =
    filter != NotificationManager.INTERRUPTION_FILTER_ALL

/** Main-thread controller. The Store callback owns guard checks, arbitration and finite playback. */
class DeviceSignals(
    context: Context,
    private val show: (owner: String, look: Ambient, durationMs: Int) -> Boolean,
    private val cancel: (owner: String) -> Unit,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("hilight", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableSettings = MutableStateFlow(DeviceSignalSettings(
        chargingEnabled = prefs.getBoolean("signals.chargingEnabled", false),
        chargingColor = prefs.getInt("signals.chargingColor", 0xFF2979FF.toInt()),
        chargedColor = prefs.getInt("signals.chargedColor", 0xFF00E676.toInt()),
        fullPercent = prefs.getInt("signals.fullPercent", 100).coerceIn(1, 100),
        faceDownBatteryEnabled = prefs.getBoolean("signals.faceDownBatteryEnabled", true),
        callsEnabled = prefs.getBoolean("signals.callsEnabled", false),
        callColor = prefs.getInt("signals.callColor", 0xFF00E676.toInt()),
        callPattern = Pattern.of(prefs.getString("signals.callPattern", "blink") ?: "blink"),
    ))
    val settings: StateFlow<DeviceSignalSettings> = mutableSettings.asStateFlow()
    private var masterEnabled = false
    private var registered = false
    private var plugged = false
    private var batteryPercent = -1
    private var previousFilter: Int? = null
    val inDoNotDisturb: Boolean
        get() = previousFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
            previousFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
            previousFilter == NotificationManager.INTERRUPTION_FILTER_NONE
    /** Conservatively blocks DND-respecting output until the listener reports DND is off. */
    val shouldSuppressForDnd: Boolean
        get() = dndBlocksSignals(previousFilter)
    private var pulseScheduled = false
    private val pulse = object : Runnable {
        override fun run() {
            pulseScheduled = false
            if (!masterEnabled || !settings.value.chargingEnabled || !plugged || batteryPercent < 0) return
            show(OWNER_CHARGING, chargingSignalLook(settings.value, batteryPercent), SIGNAL_DURATION_MS)
            // A normal Handler deliberately does not wake a sleeping device.
            pulseScheduled = true
            handler.postDelayed(this, CHARGING_INTERVAL_MS)
        }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val wasFull = batteryPercent >= settings.value.fullPercent
            batteryPercent = if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).toInt().coerceIn(0, 100) else -1
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (!plugged || batteryPercent < 0) stopCharging()
            else if (!pulseScheduled || wasFull != (batteryPercent >= settings.value.fullPercent)) {
                stopCharging()
                pulse.run()
            }
        }
    }

    fun updateSettings(transform: (DeviceSignalSettings) -> DeviceSignalSettings) {
        val old = settings.value
        val proposed = transform(old)
        val next = proposed.copy(fullPercent = proposed.fullPercent.coerceIn(1, 100))
        if (old == next) return
        prefs.edit()
            .putBoolean("signals.chargingEnabled", next.chargingEnabled)
            .putInt("signals.chargingColor", next.chargingColor)
            .putInt("signals.chargedColor", next.chargedColor)
            .putInt("signals.fullPercent", next.fullPercent)
            .putBoolean("signals.faceDownBatteryEnabled", next.faceDownBatteryEnabled)
            .putBoolean("signals.callsEnabled", next.callsEnabled)
            .putInt("signals.callColor", next.callColor)
            .putString("signals.callPattern", next.callPattern.key)
            .apply()
        mutableSettings.value = next
        if (!next.faceDownBatteryEnabled) cancel(OWNER_BATTERY_GAUGE)
        if (!next.callsEnabled) cancel(OWNER_CALL)
        syncBatteryRegistration()
        if (old.chargingColor != next.chargingColor || old.chargedColor != next.chargedColor || old.fullPercent != next.fullPercent) {
            stopCharging()
            pulse.run()
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        masterEnabled = enabled
        syncBatteryRegistration()
        if (!enabled) {
            cancel(OWNER_BATTERY_GAUGE)
            cancel(OWNER_CALL)
        }
    }

    fun onInterruptionFilterChanged(filter: Int) {
        previousFilter = filter
    }

    fun onFaceDown(batteryPct: Int = -1) {
        if (!masterEnabled || !settings.value.faceDownBatteryEnabled) return
        val currentPct = if (batteryPct >= 0) batteryPct else {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = app.registerReceiver(null, filter)
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100) / scale else 50
        }
        val activeLeds = ((currentPct / 100f) * 8).roundToInt().coerceIn(1, 8)
        val color = when {
            currentPct <= 15 -> 0xFFFF1744.toInt() // Red warning
            currentPct <= 30 -> 0xFFFF6D00.toInt() // Amber
            else -> 0xFF00E676.toInt()            // Emerald green
        }
        val perLed = List(8) { i -> if (i < activeLeds) color else 0xFF000000.toInt() }
        show(
            OWNER_BATTERY_GAUGE,
            Ambient(pattern = Pattern.CUSTOM, perLed = perLed, brightness = 0.85f),
            2000,
        )
    }

    fun onCallSignal() {
        if (masterEnabled && settings.value.callsEnabled) {
            show(OWNER_CALL, Ambient(pattern = settings.value.callPattern, color = settings.value.callColor, speedMs = 800), SIGNAL_DURATION_MS)
        }
    }

    private fun syncBatteryRegistration() {
        val shouldRegister = masterEnabled && settings.value.chargingEnabled
        if (shouldRegister && !registered) {
            registered = true
            app.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        } else if (!shouldRegister) {
            if (registered) {
                app.unregisterReceiver(batteryReceiver)
                registered = false
            }
            plugged = false
            batteryPercent = -1
            stopCharging()
        }
    }

    private fun stopCharging() {
        handler.removeCallbacks(pulse)
        pulseScheduled = false
        cancel(OWNER_CHARGING)
    }

    companion object {
        const val OWNER_CHARGING = "device-signal:charging"
        const val OWNER_BATTERY_GAUGE = "device-signal:battery-gauge"
        const val OWNER_DND = "device-signal:dnd"
        const val OWNER_CALL = "device-signal:call"
        const val SIGNAL_DURATION_MS = 2000
        private const val CHARGING_INTERVAL_MS = 15_000L
    }
}
