package com.hilight.studio

import androidx.annotation.StringRes
import com.hilight.core.RendererContract
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Patterns the renderer understands.
 *
 * [cycleMeaningRes] spells out what one "cycle" is for each pattern, because it means something
 * different every time. [usesSpeed] is false for the patterns whose maths ignore speedMs entirely —
 * those must not show a cycle slider that does nothing.
 *
 * The labels are resource ids rather than strings because this enum is read from the renderer's state
 * layer and from a Quick Settings tile as well as from Compose, and none of those has a Context to
 * resolve a string with at the point the enum is declared.
 */
enum class Pattern(
    val key: String,
    @StringRes val labelRes: Int,
    val usesSpeed: Boolean = true,
    @StringRes val cycleMeaningRes: Int? = null,
    /**
     * Set only where the full name does not fit a narrow control.
     *
     * The Live tab's effect tiles and the per-LED fill buttons give a pattern a third of a row, which
     * "Rainbow" survives and レインボー does not — it wraps and then clips. Read through
     * [shortLabelRes], which falls back to the full name.
     */
    @StringRes private val narrowLabelRes: Int? = null,
) {
    OFF("off", R.string.pattern_off, usesSpeed = false),
    SOLID("solid", R.string.pattern_solid, usesSpeed = false),
    GRADIENT("gradient", R.string.pattern_gradient, usesSpeed = false),
    BREATHE("breathe", R.string.pattern_breathe, cycleMeaningRes = R.string.cycle_breathe),
    BLINK("blink", R.string.pattern_blink, cycleMeaningRes = R.string.cycle_blink),
    PULSE("pulse", R.string.pattern_pulse, cycleMeaningRes = R.string.cycle_pulse),
    CHASE("chase", R.string.pattern_chase, cycleMeaningRes = R.string.cycle_chase),
    COMET("comet", R.string.pattern_comet, cycleMeaningRes = R.string.cycle_comet),
    WAVE("wave", R.string.pattern_wave, cycleMeaningRes = R.string.cycle_wave),
    RAINBOW(
        "rainbow", R.string.pattern_rainbow, cycleMeaningRes = R.string.cycle_rainbow,
        narrowLabelRes = R.string.pattern_rainbow_short,
    ),
    METER("meter", R.string.pattern_meter, cycleMeaningRes = R.string.cycle_meter),
    STROBE("strobe", R.string.pattern_strobe, cycleMeaningRes = R.string.cycle_strobe),
    HEARTBEAT("heartbeat", R.string.pattern_heartbeat, cycleMeaningRes = R.string.cycle_heartbeat),
    BOUNCE("bounce", R.string.pattern_bounce, cycleMeaningRes = R.string.cycle_bounce),
    RADAR("radar", R.string.pattern_radar, cycleMeaningRes = R.string.cycle_radar),
    CONVERGE("converge", R.string.pattern_converge, cycleMeaningRes = R.string.cycle_converge),
    GLITCH("glitch", R.string.pattern_glitch, cycleMeaningRes = R.string.cycle_glitch),
    SCANNER("scanner", R.string.pattern_scanner, cycleMeaningRes = R.string.cycle_scanner),
    DIVERGE("diverge", R.string.pattern_diverge, cycleMeaningRes = R.string.cycle_diverge),
    CLOCKWISE_FILL("clockwise_fill", R.string.pattern_clockwise_fill, cycleMeaningRes = R.string.cycle_clockwise_fill),
    AURORA("aurora", R.string.pattern_aurora, cycleMeaningRes = R.string.cycle_aurora),
    FIRE("fire", R.string.pattern_fire, usesSpeed = false),
    RIPPLE("ripple", R.string.pattern_ripple, cycleMeaningRes = R.string.cycle_ripple),
    SPARKLE("sparkle", R.string.pattern_sparkle, cycleMeaningRes = R.string.cycle_sparkle),
    RANDOM("random", R.string.pattern_random, usesSpeed = false),
    VALERIA(
        "valeria", R.string.pattern_valeria, cycleMeaningRes = R.string.cycle_valeria,
        narrowLabelRes = R.string.pattern_valeria_short,
    ),
    MORSE_VALERIA(
        "morse", R.string.pattern_morse, cycleMeaningRes = R.string.cycle_morse,
        narrowLabelRes = R.string.pattern_morse_short,
    ),
    CUSTOM("custom", R.string.pattern_custom, usesSpeed = false);

    /** The name to show where a third of a row is all there is. */
    @get:StringRes
    val shortLabelRes: Int get() = narrowLabelRes ?: labelRes

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun of(key: String) = BY_KEY[key] ?: SOLID
    }
}

enum class Trigger { NOTIFICATION, FOREGROUND, SCHEDULED }
enum class EventTarget { ALL, MESSAGES_ONLY, CALLS_ONLY }
enum class AlertSource(val key: String) {
    NOTIFICATION("notification"), PREVIEW("preview"), FOREGROUND("foreground")
}

/** A continuous Android privacy operation observed by the privileged renderer. */
enum class PrivacyActivity(val key: String, val appOp: String) {
    MICROPHONE("microphone", "android:record_audio"),
    CAMERA("camera", "android:camera");

    companion object {
        private val BY_KEY = entries.associateBy { it.key }
        fun of(key: String): PrivacyActivity? = BY_KEY[key]
    }
}

/** The always-on look: what HiLight shows when nothing else is happening. */
data class Ambient(
    val pattern: Pattern = Pattern.OFF,
    val color: Int = CalibratedLedColors.PURPLE,
    val secondColor: Int = CalibratedLedColors.CYAN,
    val perLed: List<Int> = DEFAULT_PER_LED,
    val brightness: Float = 0.7f,
    val speedMs: Int = 2500,
    val speedMultiplier: Float = 1.0f,
    val maxBrightness: Float = 1.0f,
    val rainbowSpread: Boolean = true,
    val randomIntervalMs: Int = 1500,
    val randomPerLed: Boolean = true,
    val randomSmooth: Boolean = true,
    val randomSaturation: Float = 1f,
    val rotateMs: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", pattern.key)
        put("brightness", (brightness * maxBrightness).toDouble().coerceIn(0.0, 1.0))
        put("speedMs", max(60L, (speedMs / max(0.01f, speedMultiplier)).toLong()))
        put("spread", rainbowSpread)
        put("randomIntervalMs", randomIntervalMs)
        put("randomPerLed", randomPerLed)
        put("randomSmooth", randomSmooth)
        put("randomSaturation", randomSaturation.toDouble())
        put("rotateMs", rotateMs)
        when (pattern) {
            Pattern.CUSTOM -> put("colors", JSONArray().also { a -> perLed.forEach { a.put(it.toUInt().toLong()) } })
            Pattern.GRADIENT -> put(
                "colors",
                JSONArray().put(color.toUInt().toLong()).put(secondColor.toUInt().toLong())
            )
            else -> put("color", color.toUInt().toLong())
        }
    }

    companion object {
        private val DEFAULT_PER_LED = List(LED_COUNT) { CalibratedLedColors.PURPLE }

        fun fromJson(o: JSONObject) = Ambient(
            pattern = Pattern.of(o.optString("pattern", "off")),
            color = o.optLong("color", CalibratedLedColors.PURPLE.toLong()).toInt(),
            secondColor = o.optLong("secondColor", CalibratedLedColors.CYAN.toLong()).toInt(),
            perLed = o.optJSONArray("perLed")?.let { a ->
                (0 until a.length()).map { a.optLong(it).toInt() }
            }?.takeIf { it.size == LED_COUNT } ?: DEFAULT_PER_LED,
            brightness = o.optDouble("brightness", 0.7).toFloat(),
            speedMs = o.optInt("speedMs", 2500),
            speedMultiplier = o.optDouble("speedMultiplier", 1.0).toFloat(),
            maxBrightness = o.optDouble("maxBrightness", 1.0).toFloat(),
            rainbowSpread = o.optBoolean("rainbowSpread", true),
            randomIntervalMs = o.optInt("randomIntervalMs", 1500),
            randomPerLed = o.optBoolean("randomPerLed", true),
            randomSmooth = o.optBoolean("randomSmooth", true),
            randomSaturation = o.optDouble("randomSaturation", 1.0).toFloat(),
            rotateMs = o.optInt("rotateMs", 0),
        )
    }

    /** Local persistence form (keeps UI-only fields the helper does not need). */
    fun toPrefsJson(): JSONObject = JSONObject().apply {
        put("pattern", pattern.key)
        put("color", color.toUInt().toLong())
        put("secondColor", secondColor.toUInt().toLong())
        put("perLed", JSONArray().also { a -> perLed.forEach { a.put(it.toUInt().toLong()) } })
        put("brightness", brightness.toDouble())
        put("speedMs", speedMs)
        put("speedMultiplier", speedMultiplier.toDouble())
        put("maxBrightness", maxBrightness.toDouble())
        put("rainbowSpread", rainbowSpread)
        put("randomIntervalMs", randomIntervalMs)
        put("randomPerLed", randomPerLed)
        put("randomSmooth", randomSmooth)
        put("randomSaturation", randomSaturation.toDouble())
        put("rotateMs", rotateMs)
    }
}

/** One "show X for app Y" rule. */
data class AppRule(
    val pkg: String,
    val label: String,
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger.NOTIFICATION,
    val pattern: Pattern = Pattern.PULSE,
    val randomColor: Boolean = false,
    val color: Int = CalibratedLedColors.EMERALD_GREEN,
    val durationMs: Int = 10_000,
    val speedMs: Int = 800,
    val brightness: Float = 1f,
    val onlyWhenScreenOff: Boolean = false,
    val onlyWhenFaceDown: Boolean = false,
    val keyword: String = "",
    val conversationKey: String? = null,
    val conversationName: String? = null,
    val includeGroups: Boolean = false,
    val conversationIsGroup: Boolean = false,
    val look: Ambient? = null,
    val ignoreSilent: Boolean = false,
    val excludedPackages: Set<String> = emptySet(),
    val eventTarget: EventTarget = EventTarget.ALL,
    val knockEnabled: Boolean = false,
) {
    fun effectiveLook(colorOverride: Int = color): Ambient =
        (look ?: Ambient(secondColor = colorOverride, randomIntervalMs = 500)).copy(
            pattern = pattern, color = colorOverride, speedMs = speedMs, brightness = brightness,
        )

    fun withLook(value: Ambient): AppRule = copy(
        look = value, pattern = value.pattern, color = value.color,
        speedMs = value.speedMs, brightness = value.brightness, randomColor = false,
    )

    val isCatchAll: Boolean get() = pkg == ANY_APP
    val isConversationRule: Boolean
        get() = !conversationKey.isNullOrBlank() || !conversationName.isNullOrBlank()

    val id: String = "$pkg|${trigger.name}|${conversationKey ?: conversationName ?: ""}|${eventTarget.name}"

    fun toPrefsJson(): JSONObject = JSONObject().apply {
        put("pkg", pkg)
        put("label", label)
        put("enabled", enabled)
        put("trigger", trigger.name)
        put("pattern", pattern.key)
        put("randomColor", randomColor)
        put("color", color.toUInt().toLong())
        put("durationMs", durationMs)
        put("speedMs", speedMs)
        put("brightness", brightness.toDouble())
        put("onlyWhenScreenOff", onlyWhenScreenOff)
        put("onlyWhenFaceDown", onlyWhenFaceDown)
        put("keyword", keyword)
        conversationKey?.let { put("conversationKey", it) }
        conversationName?.let { put("conversationName", it) }
        put("includeGroups", includeGroups)
        put("conversationIsGroup", conversationIsGroup)
        look?.let { put("look", it.toPrefsJson()) }
        put("ignoreSilent", ignoreSilent)
        put("excludedPackages", JSONArray().also { a -> excludedPackages.sorted().forEach(a::put) })
        put("eventTarget", eventTarget.name)
        put("knockEnabled", knockEnabled)
    }

    companion object {
        const val ANY_APP = "*"

        fun fromJson(o: JSONObject) = AppRule(
            pkg = o.getString("pkg"),
            label = o.optString("label", o.getString("pkg")),
            enabled = o.optBoolean("enabled", true),
            trigger = runCatching { Trigger.valueOf(o.optString("trigger", "NOTIFICATION")) }
                .getOrDefault(Trigger.NOTIFICATION),
            pattern = Pattern.of(o.optString("pattern", "pulse")),
            randomColor = o.optBoolean("randomColor", false),
            color = o.optLong("color", CalibratedLedColors.EMERALD_GREEN.toLong()).toInt(),
            durationMs = o.optInt("durationMs", 10_000),
            speedMs = o.optInt("speedMs", 800),
            brightness = o.optDouble("brightness", 1.0).toFloat(),
            onlyWhenScreenOff = o.optBoolean("onlyWhenScreenOff", false),
            onlyWhenFaceDown = o.optBoolean("onlyWhenFaceDown", false),
            keyword = o.optString("keyword", ""),
            conversationKey = o.optString("conversationKey", "").takeIf { it.isNotEmpty() },
            conversationName = o.optString("conversationName", "").takeIf { it.isNotEmpty() },
            includeGroups = o.optBoolean("includeGroups", false),
            conversationIsGroup = o.optBoolean("conversationIsGroup", false),
            look = o.optJSONObject("look")?.let(Ambient::fromJson),
            ignoreSilent = o.optBoolean("ignoreSilent", false),
            excludedPackages = o.optJSONArray("excludedPackages")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }.toSet()
            } ?: emptySet(),
            eventTarget = runCatching {
                EventTarget.valueOf(o.optString("eventTarget", EventTarget.ALL.name))
            }.getOrDefault(EventTarget.ALL),
            knockEnabled = o.optBoolean("knockEnabled", false),
        )
    }
}

/** One microphone/camera activity signal, deliberately separate from notification/app-open rules. */
data class PrivacyRule(
    val activity: PrivacyActivity,
    val pkg: String = AppRule.ANY_APP,
    val appLabel: String = "",
    val enabled: Boolean = true,
    val pattern: Pattern = Pattern.BLINK,
    val color: Int,
    val secondColor: Int = CalibratedLedColors.CYAN,
    val lightMs: Int = DEFAULT_LIGHT_MS,
    val cooldownMs: Int = DEFAULT_COOLDOWN_MS,
    val speedMs: Int = 800,
    val brightness: Float = 1f,
) {
    val id: String = "${activity.key}|$pkg"
    val isCatchAll: Boolean get() = pkg == AppRule.ANY_APP

    fun toPrefsJson(): JSONObject = JSONObject().apply {
        put("activity", activity.key)
        put("pkg", pkg)
        put("appLabel", appLabel)
        put("enabled", enabled)
        put("pattern", pattern.key)
        put("color", color.toUInt().toLong())
        put("secondColor", secondColor.toUInt().toLong())
        put("lightMs", lightMs)
        put("cooldownMs", cooldownMs)
        put("speedMs", speedMs)
        put("brightness", brightness.toDouble())
    }

    fun toRendererJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("activity", activity.key)
        put("pkg", pkg)
        put("pattern", pattern.key)
        if (pattern == Pattern.GRADIENT) {
            put(
                "colors",
                JSONArray()
                    .put(color.toUInt().toLong())
                    .put(secondColor.toUInt().toLong()),
            )
        } else {
            put("color", color.toUInt().toLong())
        }
        put("lightMs", lightMs.coerceIn(MIN_PHASE_MS, MAX_PHASE_MS))
        put("cooldownMs", cooldownMs.coerceIn(MIN_PHASE_MS, MAX_PHASE_MS))
        put("speedMs", speedMs.coerceIn(100, 10_000))
        put("brightness", brightness.coerceIn(0.05f, 1f).toDouble())
    }

    companion object {
        const val DEFAULT_LIGHT_MS = 10_000
        const val DEFAULT_COOLDOWN_MS = 10_000
        const val MIN_PHASE_MS = 1_000
        const val MAX_PHASE_MS = 60_000

        val selectablePatterns: List<Pattern>
            get() = Pattern.entries.filter { it != Pattern.OFF && it != Pattern.CUSTOM }

        fun default(
            activity: PrivacyActivity,
            pkg: String = AppRule.ANY_APP,
            appLabel: String = "",
        ): PrivacyRule = PrivacyRule(
            activity = activity,
            pkg = pkg,
            appLabel = appLabel,
            color = when (activity) {
                PrivacyActivity.MICROPHONE -> CalibratedLedColors.RED
                PrivacyActivity.CAMERA -> CalibratedLedColors.EMERALD_GREEN
            },
        )

        fun fromJson(o: JSONObject): PrivacyRule? {
            val activity = PrivacyActivity.of(o.optString("activity")) ?: return null
            val defaults = default(activity)
            return PrivacyRule(
                activity = activity,
                pkg = o.optString("pkg", AppRule.ANY_APP),
                appLabel = o.optString("appLabel", ""),
                enabled = o.optBoolean("enabled", true),
                pattern = Pattern.of(o.optString("pattern", defaults.pattern.key)),
                color = o.optLong("color", defaults.color.toUInt().toLong()).toInt(),
                secondColor = o.optLong(
                    "secondColor",
                    defaults.secondColor.toUInt().toLong(),
                ).toInt(),
                lightMs = o.optInt("lightMs", DEFAULT_LIGHT_MS)
                    .coerceIn(MIN_PHASE_MS, MAX_PHASE_MS),
                cooldownMs = o.optInt("cooldownMs", DEFAULT_COOLDOWN_MS)
                    .coerceIn(MIN_PHASE_MS, MAX_PHASE_MS),
                speedMs = o.optInt("speedMs", defaults.speedMs).coerceIn(100, 10_000),
                brightness = o.optDouble("brightness", defaults.brightness.toDouble()).toFloat()
                    .coerceIn(0.05f, 1f),
            )
        }
    }
}

/** A saved look. Only the ambient config is stored; rules are separate. */
data class Preset(val name: String, val ambient: Ambient) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("ambient", ambient.toPrefsJson())
    }

    companion object {
        fun fromJson(o: JSONObject) = Preset(
            name = o.optString("name", "Preset"),
            ambient = Ambient.fromJson(o.getJSONObject("ambient")),
        )
    }
}

enum class Suppression(@StringRes val shortRes: Int) {
    QUIET_HOURS(R.string.suppression_quiet_hours),
    LOW_BATTERY(R.string.suppression_low_battery),
    POWER_SAVER(R.string.suppression_power_saver),
    SCREEN_ON(R.string.suppression_screen_on),
    NOT_FACE_DOWN(R.string.suppression_not_face_down),
}

object Limits {
    const val BATTERY_DEFAULT_PCT = 10
    const val BATTERY_MIN_PCT = 5
    const val BATTERY_MAX_PCT = 50
    const val AMBIENT_DEFAULT_MS = 30_000
    const val AMBIENT_MAX_MS = 300_000
    const val RULE_DEFAULT_MS = 10_000
    const val RULE_MAX_MS = 60_000
    const val WARN_ABOVE_MS = 30_000
}

enum class RendererCompatibility { CURRENT, INCOMPATIBLE, UNKNOWN }

data class HelperStatus(
    val alive: Boolean,
    val ageMs: Long = -1,
    val pid: Int = -1,
    val uid: Int = -1,
    val owner: String = "",
    val rendererInstanceId: String = "",
    val identityResolved: Boolean = true,
    val ledCount: Int = 0,
    val sessionOpen: Boolean = false,
    val blackClearPending: Boolean = false,
    val blackClearTerminal: Boolean = false,
    val blackClearResult: String = "not_requested",
    val blackClearAttemptResult: String = "not_requested",
    val blackClearStage: String = "idle",
    val blackClearTimestampElapsedMs: Long = 0,
    val blackClearCycleId: Long = 0,
    val blackClearCycleSource: String = "automatic",
    val blackClearAttemptsUsed: Int = 0,
    val blackClearAttemptsRemaining: Int = 0,
    val blackClearStopAttemptAvailable: Boolean = false,
    val blackClearCloseFailures: Int = 0,
    val blackClearUnreleasedFatal: Boolean = false,
    val lightMinUpdatePeriodMs: Long = 0,
    val blackClearStrategy: String = "unknown",
    val blackClearStrategyVersion: Int = 0,
    val rendererVersionCode: Int = -1,
    val rendererVersionName: String = "",
    val rendererContractVersion: Int = -1,
    val rendererImplementationRevision: Int = -1,
    val rendererStatusSchemaVersion: Int = -1,
    val rendererClearAlgorithmVersion: Int = -1,
    val rendererServiceVersion: Int = -1,
    val rendererReady: Boolean = false,
    val mode: String = "-",
    val ambientRemainingMs: Long = 0,
    val ambientHeld: Boolean = false,
    val resting: Boolean = false,
    val dutyPct: Int = 0,
    val appliedStateRevision: Long = 0,
    val receivedStateRevision: Long = appliedStateRevision,
    val settledStateRevision: Long = 0,
    val releasedStateRevision: Long = 0,
    val lastSeenManualBlackClearRequestId: Long = 0,
    val lastAcceptedManualBlackClearRequestId: Long = 0,
    val privacyObserverEnabled: Boolean = false,
    val privacyObserverState: String = "stopped",
    val privacyPhase: String = "inactive",
) {
    val rendererCompatibility: RendererCompatibility
        get() = when {
            rendererContractVersion < 0 || rendererImplementationRevision < 0 ||
                    rendererStatusSchemaVersion < 0 || rendererClearAlgorithmVersion < 0 ->
                RendererCompatibility.UNKNOWN
            RendererContract.isCompatible(
                rendererContractVersion,
                rendererImplementationRevision,
                rendererStatusSchemaVersion,
                rendererClearAlgorithmVersion,
            ) -> RendererCompatibility.CURRENT
            else -> RendererCompatibility.INCOMPATIBLE
        }

    val rendererStale: Boolean
        get() = (alive || pid > 0) &&
                (rendererCompatibility != RendererCompatibility.CURRENT || !rendererReady)
}

data class RendererStatusSnapshot(
    val status: HelperStatus,
    val selectedTransport: Transport,
    val activeTransport: Transport,
    val capturedAtEpochMs: Long,
)

const val LED_COUNT = 8

/**
 * Palette calibrata specificamente per LED fisici RGB.
 *
 * I display tradizionali usano colori con saturazione bilanciata per pannelli OLED/LCD (es. #FFFF00 o #00FFFF).
 * Sui LED fisici, i canali multi-colore saturano la percezione retinica e la lente ottica, sbiancando la luce.
 * Questi valori compensano lo squilibrio fotopico e preservano una cromia solida, densa e riconoscibile.
 */
object CalibratedLedColors {
    val RED = 0xFFFF0010.toInt()            // Rosso puro solido
    val ORANGE = 0xFFFF3B00.toInt()         // Arancione saturo (non vira a giallino)
    val AMBER_YELLOW = 0xFFFF8C00.toInt()   // Giallo ambra caldo (elimina la luce bianca/slavata)
    val EMERALD_GREEN = 0xFF00E630.toInt()  // Verde smeraldo puro
    val CYAN = 0xFF00C8B0.toInt()           // Ciano/Turchese bilanciato (non abbaglia di bianco)
    val SAPPHIRE_BLUE = 0xFF0033FF.toInt()  // Blu profondo intenso
    val PURPLE = 0xFF7B1FA2.toInt()         // Viola saturo equilibrato
    val FUCHSIA = 0xFFE91E63.toInt()        // Magenta / Deep Pink vibrante
    val PINK = 0xFFFF1493.toInt()           // Rosa ottico evidente (non slavato)
    val WARM_WHITE = 0xFFFFE0BD.toInt()     // Bianco caldo a ~4000K

    val PALETTE: List<Int> = listOf(
        RED,
        ORANGE,
        AMBER_YELLOW,
        EMERALD_GREEN,
        CYAN,
        SAPPHIRE_BLUE,
        PURPLE,
        FUCHSIA,
        PINK,
        WARM_WHITE,
    )
}