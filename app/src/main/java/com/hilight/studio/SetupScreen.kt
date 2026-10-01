package com.hilight.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADB_PHONE_RESET =
    "live=1; i=0; while [ ${'$'}i -lt 65 ] && [ -n \"${'$'}live\" ]; do live=\"\"; " +
            "for d in /proc/[0-9]*; do p=${'$'}{d#/proc/}; " +
            "c=${'$'}(tr \"\\000\" \" \" < ${'$'}d/cmdline 2>/dev/null); " +
            "if [ -z \"${'$'}c\" ]; then e=${'$'}(readlink ${'$'}d/exe 2>/dev/null); " +
            "x=${'$'}{e##*/}; if [ \"${'$'}x\" = app_process ] || " +
            "[ \"${'$'}x\" = app_process32 ] || " +
            "[ \"${'$'}x\" = app_process64 ]; then exit 1; fi; continue; fi; " +
            "set -- ${'$'}c; " +
            "x=${'$'}{1##*/}; if { { [ \"${'$'}x\" = app_process ] || " +
            "[ \"${'$'}x\" = app_process32 ] || [ \"${'$'}x\" = app_process64 ]; } && " +
            "[ \"${'$'}{2:-x}\" = / ] && " +
            "[ \"${'$'}{3:-x}\" = com.hilight.core.AdbHelper ]; } || " +
            "[ \"${'$'}{1:-x}\" = com.hilight.studio:hilight ]; then " +
            "kill -TERM ${'$'}p 2>/dev/null || exit 1; live=1; fi; done; " +
            "[ -n \"${'$'}live\" ] && sleep 0.1; i=${'$'}((i + 1)); done; " +
            "[ -z \"${'$'}live\" ] || exit 1"

private const val ADB_PHONE_RESET_CMD =
    "live=1; i=0; while [ ${'$'}i -lt 65 ] && [ ${'$'}live = 1 ]; do live=0; " +
            "for d in /proc/[0-9]*; do p=${'$'}{d#/proc/}; " +
            "c=${'$'}(tr '\\000' ' ' < ${'$'}d/cmdline 2>/dev/null); set -- ${'$'}c; " +
            "if [ ${'$'}# -eq 0 ]; then e=${'$'}(readlink ${'$'}d/exe 2>/dev/null); " +
            "x=${'$'}{e##*/}; if [ ${'$'}{x:-none} = app_process ] || " +
            "[ ${'$'}{x:-none} = app_process32 ] || [ ${'$'}{x:-none} = app_process64 ]; " +
            "then exit 1; fi; continue; fi; " +
            "x=${'$'}{1##*/}; if { { [ ${'$'}x = app_process ] || " +
            "[ ${'$'}x = app_process32 ] || [ ${'$'}x = app_process64 ]; } && " +
            "[ ${'$'}{2:-x} = / ] && " +
            "[ ${'$'}{3:-x} = com.hilight.core.AdbHelper ]; } || " +
            "[ ${'$'}{1:-x} = com.hilight.studio:hilight ]; then " +
            "kill -TERM ${'$'}p 2>/dev/null || exit 1; live=1; fi; done; " +
            "[ ${'$'}live = 1 ] && sleep 0.1; i=${'$'}((i + 1)); done; " +
            "[ ${'$'}live = 0 ] || exit 1"

const val ADB_RESET = "adb shell '$ADB_PHONE_RESET'"

const val ADB_COMMAND =
    "adb shell '$ADB_PHONE_RESET; " +
            "instance=adb-${'$'}(cat /proc/sys/kernel/random/uuid); " +
            "CLASSPATH=${'$'}(pm path com.hilight.studio | head -1 | cut -d: -f2) " +
            "nohup app_process / com.hilight.core.AdbHelper --owner adb " +
            "--instance \"${'$'}instance\" --exclusive > /data/local/tmp/hilight.log 2>&1 &'"

const val ADB_COMMAND_CMD =
    "adb shell \"$ADB_PHONE_RESET_CMD; " +
            "instance=adb-${'$'}(cat /proc/sys/kernel/random/uuid); " +
            "CLASSPATH=${'$'}(pm path com.hilight.studio | head -1 | cut -d: -f2) " +
            "nohup app_process / com.hilight.core.AdbHelper --owner adb " +
            "--instance ${'$'}instance --exclusive > /data/local/tmp/hilight.log 2>&1 &\""

@Composable
fun CollapsibleCard(
    title: String,
    initiallyExpanded: Boolean = false,
    tone: Int = 1,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrowRotation")

    PixelCard(tone = tone) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(title)
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun SetupScreen(store: Store) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    val status by store.status.collectAsStateWithLifecycle()
    val masterEnabled by store.enabled.collectAsStateWithLifecycle()
    val manualCleanupPending by store.manualLedCleanupPending.collectAsStateWithLifecycle()
    val manualCleanupInProgress = manualCleanupPending ||
            (status.blackClearPending && status.blackClearCycleSource == "manual")
    val transport by store.transport.collectAsStateWithLifecycle()
    val shizukuState by store.shizuku.state.collectAsStateWithLifecycle()
    val notifyShizukuLoss by store.shizukuRecovery.enabled.collectAsStateWithLifecycle()
    val rootState by store.root.state.collectAsStateWithLifecycle()
    val priority by store.priority.collectAsStateWithLifecycle()
    val dynamicColor by store.dynamicColor.collectAsStateWithLifecycle()
    val timeoutMs by store.ambientTimeoutMs.collectAsStateWithLifecycle()
    val quietEnabled by store.quietEnabled.collectAsStateWithLifecycle()
    val quietStart by store.quietStart.collectAsStateWithLifecycle()
    val quietEnd by store.quietEnd.collectAsStateWithLifecycle()
    val quietByDay by store.quietByDay.collectAsStateWithLifecycle()
    val quietDays by store.quietDays.collectAsStateWithLifecycle()
    val batteryGuard by store.batteryGuard.collectAsStateWithLifecycle()
    val batteryMinPct by store.batteryMinPct.collectAsStateWithLifecycle()
    val saverGuard by store.saverGuard.collectAsStateWithLifecycle()
    val suppression by store.suppression.collectAsStateWithLifecycle()
    val respectDnd by store.respectDnd.collectAsStateWithLifecycle()
    val quietDim by store.quietDim.collectAsStateWithLifecycle()
    val quietDimPct by store.quietDimPct.collectAsStateWithLifecycle()
    val screenOffOnly by store.screenOffOnly.collectAsStateWithLifecycle()
    val faceDownOnly by store.faceDownOnly.collectAsStateWithLifecycle()
    val faceDownNoticeAccepted by store.faceDownNoticeAccepted.collectAsStateWithLifecycle()
    val faceDownState by store.faceDownState.collectAsStateWithLifecycle()
    val faceDownSensorAvailable = remember(ctx) { ForegroundWatcher.hasFaceDownSensor(ctx) }
    val glowSuppression = suppression?.takeIf {
        it.settingsSection() == SettingsSuppressionSection.GLOW
    }
    val pauseSuppression = suppression?.takeIf {
        it.settingsSection() == SettingsSuppressionSection.PAUSE
    }

    var notifAccess by remember { mutableStateOf(hasNotificationAccess(ctx)) }
    var usageAccess by remember { mutableStateOf(ForegroundWatcher.hasUsageAccess(ctx)) }
    var inspecting by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var checkingForUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var selfTestCountdown by remember { mutableIntStateOf(0) }
    var selfTestWarning by remember { mutableStateOf<String?>(null) }
    var confirmingFaceDown by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val updateScope = rememberCoroutineScope()
    val ioScope = rememberCoroutineScope()
    val conversations by store.conversations.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            ioScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val exportData = store.exportBackupJson()
                        ctx.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(exportData.toByteArray(Charsets.UTF_8))
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Backup salvato con successo!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Errore salvataggio: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ioScope.launch {
                try {
                    val jsonString = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                            it.readText()
                        } ?: ""
                    }
                    val success = withContext(Dispatchers.IO) {
                        store.importBackupJson(jsonString)
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(ctx, "Configurazione ripristinata!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "File di backup non valido", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Errore importazione: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val testWarning: (Boolean) -> String? = { scheduling ->
        val reason = store.notificationTestSuppressionReason(scheduling)
        val notificationManager = ctx.getSystemService(android.app.NotificationManager::class.java)
        when {
            !store.enabled.value -> resources.getString(R.string.setup_test_blocked_hilight_off)
            !hasNotificationAccess(ctx) -> resources.getString(R.string.setup_test_needs_listener)
            store.respectDnd.value && store.deviceSignals.inDoNotDisturb ->
                resources.getString(R.string.setup_test_blocked_dnd)
            !notificationManager.areNotificationsEnabled() ||
                    notificationManager.getNotificationChannel("selftest")?.importance ==
                    android.app.NotificationManager.IMPORTANCE_NONE ->
                resources.getString(R.string.setup_test_needs_notifications)
            reason != null -> resources.getString(
                R.string.test_blocked_by_guard, resources.getString(reason.shortRes),
            )
            else -> null
        }
    }

    val postSelfTest: () -> Unit = {
        val warning = testWarning(false)
        selfTestWarning = warning
        if (warning == null) postSelfTestNotification(ctx.applicationContext)
        else Toast.makeText(ctx.applicationContext, warning, Toast.LENGTH_LONG).show()
    }

    val checkForUpdates: () -> Unit = {
        checkingForUpdates = true
        updateScope.launch {
            updateResult = withContext(Dispatchers.IO) {
                GitHubUpdateChecker.check(BuildConfig.VERSION_NAME)
            }
            checkingForUpdates = false
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            while (true) {
                val notif = hasNotificationAccess(ctx)
                val usage = ForegroundWatcher.hasUsageAccess(ctx)
                withContext(Dispatchers.Main) {
                    notifAccess = notif
                    usageAccess = usage
                    store.shizuku.refresh()
                }
                delay(1500)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            val tabTitles = listOf("Comportamento", "Riposo", "Sistema")
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.titleSmall) }
                )
            }
        }

        // Colonna senza .verticalScroll: lo scrolling e gestito dal contenitore dello schermo
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 1: Comportamento
                    PixelCard(tone = 2) {
                        SectionTitle(
                            stringResource(R.string.setup_auto_off_title),
                            trailing = { Caption(formatDuration(timeoutMs)) },
                        )
                        Caption(stringResource(R.string.setup_auto_off_body))
                        Caption(stringResource(R.string.setup_auto_off_protection))
                        GatedDurationSlider(
                            label = stringResource(R.string.setup_stay_on_for),
                            valueMs = timeoutMs,
                            minMs = 5_000,
                            safeMaxMs = Limits.WARN_ABOVE_MS,
                            extendedMaxMs = Limits.AMBIENT_MAX_MS,
                            unlockLabel = stringResource(R.string.setup_allow_five_minutes),
                            warnFirst = stringResource(R.string.setup_warn_long_title) to
                                    stringResource(R.string.setup_warn_long_body),
                            warnSecond = stringResource(R.string.setup_warn_long_confirm_title) to
                                    stringResource(R.string.setup_warn_long_confirm_body),
                            onChange = { store.setAmbientTimeoutMs(it) },
                        )
                    }

                    PixelCard {
                        SectionTitle(
                            stringResource(R.string.setup_glow_conditions_title),
                            trailing = {
                                glowSuppression?.let { LivePill(stringResource(it.shortRes), ok = false) }
                            },
                        )
                        ToggleRow(stringResource(R.string.setup_screen_off_only), screenOffOnly) {
                            store.setScreenOffOnly(it)
                        }
                        ToggleRow(
                            label = stringResource(R.string.setup_face_down_only),
                            checked = faceDownOnly,
                            enabled = faceDownSensorAvailable || faceDownOnly,
                        ) { wanted ->
                            when {
                                !wanted -> store.setFaceDownOnly(false)
                                faceDownNoticeAccepted -> store.setFaceDownOnly(true)
                                else -> confirmingFaceDown = true
                            }
                        }
                        Caption(stringResource(R.string.face_down_caution))
                        if (!faceDownSensorAvailable) {
                            Caption(stringResource(R.string.face_down_no_sensor))
                        } else if (faceDownOnly) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Caption(stringResource(R.string.face_down_status_label))
                                LivePill(
                                    text = stringResource(
                                        when (faceDownState) {
                                            FaceDownState.INACTIVE -> R.string.face_down_state_inactive
                                            FaceDownState.STARTING -> R.string.face_down_state_starting
                                            FaceDownState.CHECKING -> R.string.face_down_state_checking
                                            FaceDownState.FACE_DOWN -> R.string.face_down_state_face_down
                                            FaceDownState.NOT_FACE_DOWN -> R.string.face_down_state_not_face_down
                                            FaceDownState.UNAVAILABLE -> R.string.face_down_state_unavailable
                                            FaceDownState.STALE -> R.string.face_down_state_stale
                                            FaceDownState.START_FAILED -> R.string.face_down_state_start_failed
                                        }
                                    ),
                                    ok = faceDownState == FaceDownState.FACE_DOWN,
                                )
                            }
                        }
                    }

                    PixelCard {
                        SectionTitle(stringResource(R.string.setup_appearance_title))
                        ToggleRow(stringResource(R.string.setup_wallpaper_colours), dynamicColor) {
                            store.setDynamicColor(it)
                        }
                    }
                }

                1 -> {
                    // TAB 2: Riposo & Batteria
                    PixelCard {
                        SectionTitle(
                            stringResource(R.string.setup_pause_conditions_title),
                            trailing = {
                                pauseSuppression?.let { LivePill(stringResource(it.shortRes), ok = false) }
                            },
                        )
                        ToggleRow(stringResource(R.string.suppression_quiet_hours), quietEnabled) {
                            store.setQuietHours(it)
                        }
                        if (quietEnabled) {
                            ToggleRow(stringResource(R.string.setup_quiet_by_day), quietByDay, onChange = store::setQuietByDay)
                            if (quietByDay) {
                                Caption(stringResource(R.string.setup_quiet_by_day_note))
                                val dayNames = remember { java.text.DateFormatSymbols.getInstance().weekdays }
                                quietDays.forEachIndexed { day, window ->
                                    val calendarDay = (day + 1) % 7 + 1
                                    ToggleRow(dayNames[calendarDay], window.enabled) {
                                        store.setQuietDay(day, window.copy(enabled = it))
                                    }
                                    if (window.enabled) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            FilledTonalButton(
                                                onClick = { pickTime(ctx, window.startMin) { store.setQuietDay(day, window.copy(startMin = it)) } },
                                                modifier = Modifier.weight(1f),
                                            ) { ButtonLabel(stringResource(R.string.setup_quiet_from, clock(window.startMin))) }
                                            FilledTonalButton(
                                                onClick = { pickTime(ctx, window.endMin) { store.setQuietDay(day, window.copy(endMin = it)) } },
                                                modifier = Modifier.weight(1f),
                                            ) { ButtonLabel(stringResource(R.string.setup_quiet_until, clock(window.endMin))) }
                                        }
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilledTonalButton(
                                        onClick = { pickTime(ctx, quietStart) { store.setQuietHours(true, startMin = it) } },
                                        modifier = Modifier.weight(1f),
                                    ) { ButtonLabel(stringResource(R.string.setup_quiet_from, clock(quietStart))) }
                                    FilledTonalButton(
                                        onClick = { pickTime(ctx, quietEnd) { store.setQuietHours(true, endMin = it) } },
                                        modifier = Modifier.weight(1f),
                                    ) { ButtonLabel(stringResource(R.string.setup_quiet_until, clock(quietEnd))) }
                                }
                            }
                            ToggleRow(stringResource(R.string.setup_quiet_dim), quietDim) { store.setQuietDim(it) }
                            if (quietDim) {
                                PixelSlider(
                                    stringResource(R.string.setup_dim_to),
                                    quietDimPct.toFloat(),
                                    2f..40f,
                                    { store.setQuietDim(true, it.toInt()) },
                                ) { stringResource(R.string.setup_percent, it.toInt()) }
                            }
                        }
                        ToggleRow(stringResource(R.string.setup_respect_dnd), respectDnd) { store.setRespectDnd(it) }
                        ToggleRow(stringResource(R.string.setup_pause_saver), saverGuard) { store.setSaverGuard(it) }
                        ToggleRow(stringResource(R.string.setup_pause_low_battery), batteryGuard) {
                            store.setBatteryGuard(it)
                        }
                        if (batteryGuard) {
                            PixelSlider(
                                stringResource(R.string.setup_pause_below),
                                batteryMinPct.toFloat(),
                                Limits.BATTERY_MIN_PCT.toFloat()..Limits.BATTERY_MAX_PCT.toFloat(),
                                { store.setBatteryGuard(true, it.toInt()) },
                            ) { stringResource(R.string.setup_percent, it.toInt()) }
                            Caption(stringResource(R.string.setup_battery_note))
                        }
                    }

                    DeviceSignalsSection(store)
                }

                2 -> {
                    // TAB 3: Sistema & Manutenzione
                    PixelCard(tone = 2) {
                        SectionTitle("Backup e Ripristino")
                        Caption("Esporta le tue regole app e promemoria in un file JSON o ripristina un backup precedente.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("hilight_backup.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                ButtonLabel("Esporta")
                            }
                            FilledTonalButton(
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                ButtonLabel("Importa")
                            }
                        }
                    }

                    PixelCard {
                        SectionTitle("Permessi di Accesso")
                        Caption("Configura i listener di sistema per rilevare le notifiche e le app in esecuzione.")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                ButtonLabel(if (notifAccess) "Notifiche ✓" else "Notifiche")
                            }
                            FilledTonalButton(
                                onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                                modifier = Modifier.weight(1f),
                            ) {
                                ButtonLabel(if (usageAccess) "Uso App ✓" else "Uso App")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(onClick = { inspecting = true }, modifier = Modifier.weight(1f)) {
                                ButtonLabel(stringResource(R.string.setup_inspector_button))
                            }
                            if (conversations.isNotEmpty()) {
                                TextButton(onClick = { forgetting = true }, modifier = Modifier.weight(1f)) {
                                    ButtonLabel(stringResource(R.string.setup_forget_chats_button))
                                }
                            }
                        }
                    }

                    CollapsibleCard(title = "Renderer Privilegiato (Root / Shizuku / ADB)", initiallyExpanded = false) {
                        val rootPresent = rootState == RootBackend.State.AVAILABLE ||
                                rootState == RootBackend.State.REQUESTING ||
                                rootState == RootBackend.State.STARTING ||
                                rootState == RootBackend.State.RUNNING

                        if (rootPresent) {
                            SectionTitle(
                                stringResource(R.string.setup_root_title),
                                trailing = {
                                    LivePill(
                                        stringResource(
                                            if (rootState == RootBackend.State.RUNNING)
                                                R.string.setup_root_active else R.string.setup_root_available
                                        ),
                                        ok = true,
                                    )
                                },
                            )
                            Caption(
                                stringResource(
                                    when (rootState) {
                                        RootBackend.State.AVAILABLE -> R.string.setup_root_available_body
                                        RootBackend.State.REQUESTING -> R.string.setup_root_requesting_body
                                        RootBackend.State.STARTING -> R.string.setup_root_starting_body
                                        else -> R.string.setup_root_active_body
                                    }
                                )
                            )
                            if (rootState == RootBackend.State.RUNNING && !status.alive) {
                                Caption(stringResource(R.string.setup_led_cleanup_renderer_unavailable))
                                TextButton(onClick = store::retryRoot) {
                                    ButtonLabel(stringResource(R.string.setup_root_retry))
                                }
                            }
                        } else {
                            val selectable = listOf(Transport.AUTO, Transport.SHIZUKU, Transport.ADB)
                            val transportLabels = selectable.associateWith { stringResource(it.labelRes) }
                            SegmentedSelector(
                                options = selectable,
                                selected = transport.takeIf { it in selectable } ?: Transport.AUTO,
                                label = { transportLabels.getValue(it) },
                                onSelect = { store.setTransport(it) },
                            )
                            if (transport == Transport.AUTO) Caption(stringResource(R.string.setup_transport_auto_note))
                            if (rootState == RootBackend.State.DENIED || rootState == RootBackend.State.ERROR) {
                                Caption(
                                    store.root.errorText() ?: stringResource(R.string.setup_root_error_body)
                                )
                                TextButton(onClick = store::retryRoot) {
                                    ButtonLabel(stringResource(R.string.setup_root_retry))
                                }
                            }

                            AnimatedContent(
                                targetState = transport,
                                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(120))) },
                                label = "transportCards",
                            ) { t ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (t != Transport.ADB) ShizukuCard(store, shizukuState)
                                    if (t != Transport.SHIZUKU) AdbCard(ctx)
                                }
                            }
                        }

                        ToggleRow(
                            stringResource(R.string.shizuku_recovery_setting),
                            notifyShizukuLoss,
                            onChange = store::setNotifyShizukuLoss
                        )
                    }

                    CollapsibleCard(title = "Diagnostica LED e Test", initiallyExpanded = false) {
                        SectionTitle(stringResource(R.string.setup_test_title))
                        Caption(stringResource(R.string.setup_test_body))
                        selfTestWarning?.let { Caption(it) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = postSelfTest,
                                enabled = selfTestCountdown == 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                ButtonLabel(stringResource(R.string.setup_test_button))
                            }
                            TextButton(
                                onClick = {
                                    if (selfTestCountdown != 0) return@TextButton
                                    val warning = testWarning(true)
                                    selfTestWarning = warning
                                    if (warning != null) {
                                        Toast.makeText(ctx.applicationContext, warning, Toast.LENGTH_LONG).show()
                                        return@TextButton
                                    }
                                    selfTestCountdown = 5
                                    updateScope.launch {
                                        try {
                                            for (remaining in 5 downTo 1) {
                                                selfTestCountdown = remaining
                                                delay(1_000)
                                            }
                                            postSelfTest()
                                        } finally {
                                            selfTestCountdown = 0
                                        }
                                    }
                                },
                                enabled = selfTestCountdown == 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                ButtonLabel(
                                    if (selfTestCountdown > 0) {
                                        stringResource(R.string.setup_test_countdown, selfTestCountdown)
                                    } else {
                                        stringResource(R.string.setup_test_delay_button)
                                    }
                                )
                            }
                        }

                        SectionTitle(stringResource(R.string.setup_led_diagnostics_title))
                        Caption(stringResource(R.string.setup_led_diagnostics_body))
                        FilledTonalButton(
                            onClick = {
                                updateScope.launch {
                                    val payload = withContext(Dispatchers.IO) {
                                        val snapshot = store.freshRendererStatusSnapshot()
                                        RendererDiagnostics.format(
                                            status = snapshot.status,
                                            selectedTransport = snapshot.selectedTransport,
                                            activeTransport = snapshot.activeTransport,
                                            appVersionName = BuildConfig.VERSION_NAME,
                                            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                                            deviceModel = Build.MODEL,
                                            buildId = Build.ID,
                                            sdkInt = Build.VERSION.SDK_INT,
                                            capturedAtEpochMs = snapshot.capturedAtEpochMs,
                                        )
                                    }
                                    copy(ctx, payload, R.string.setup_led_diagnostics_copied)
                                }
                            },
                        ) {
                            ButtonLabel(stringResource(R.string.setup_led_diagnostics_copy))
                        }

                        Caption(stringResource(R.string.setup_led_cleanup_retry_body))
                        FilledTonalButton(
                            onClick = { store.retryLedCleanup() },
                            enabled = store.isLedCleanupRetryEnabled(
                                masterEnabled = masterEnabled,
                                status = status,
                                requestPending = manualCleanupPending,
                            ),
                        ) {
                            ButtonLabel(
                                stringResource(
                                    if (manualCleanupInProgress) R.string.setup_led_cleanup_retry_pending
                                    else R.string.setup_led_cleanup_retry,
                                )
                            )
                        }

                        SectionTitle(stringResource(R.string.setup_priority_title))
                        Caption(stringResource(R.string.setup_priority_body))
                        PixelSlider(
                            stringResource(R.string.setup_priority_label),
                            priority.toFloat(),
                            -10f..10f,
                            { store.setPriority(it.toInt()) },
                        ) { it.toInt().toString() }
                    }

                    PixelCard {
                        SectionTitle(
                            stringResource(R.string.setup_updates_title),
                            trailing = {
                                Caption(
                                    stringResource(
                                        R.string.setup_updates_installed,
                                        BuildConfig.VERSION_NAME,
                                    )
                                )
                            },
                        )
                        when {
                            checkingForUpdates -> Caption(stringResource(R.string.setup_updates_checking))
                            updateResult == null -> Caption(stringResource(R.string.setup_updates_body))
                            updateResult is UpdateCheckResult.Available -> Caption(
                                stringResource(
                                    R.string.setup_updates_available,
                                    (updateResult as UpdateCheckResult.Available).release.versionName,
                                )
                            )
                            updateResult is UpdateCheckResult.Current ->
                                Caption(stringResource(R.string.setup_updates_current))
                            updateResult is UpdateCheckResult.NoPublishedRelease ->
                                Caption(stringResource(R.string.setup_updates_none))
                            else -> Caption(stringResource(R.string.setup_updates_failed))
                        }

                        val available = updateResult as? UpdateCheckResult.Available
                        if (available != null && !checkingForUpdates) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { openExternalUrl(ctx, available.release.pageUrl) }) {
                                    ButtonLabel(stringResource(R.string.setup_updates_view_release))
                                }
                                TextButton(onClick = checkForUpdates) {
                                    ButtonLabel(stringResource(R.string.setup_updates_check_again))
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = checkForUpdates,
                                enabled = !checkingForUpdates,
                            ) {
                                ButtonLabel(
                                    stringResource(
                                        if (checkingForUpdates) R.string.setup_updates_checking
                                        else R.string.setup_updates_check,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingFaceDown) {
        FaceDownConsentDialog(
            onAccepted = {
                store.acceptFaceDownNotice()
                store.setFaceDownOnly(true)
                confirmingFaceDown = false
            },
            onDismiss = { confirmingFaceDown = false },
        )
    }

    if (inspecting) {
        NotificationInspectorDialog(store) { inspecting = false }
    }

    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.setup_forget_chats_title)) },
            text = {
                Text(
                    stringResource(R.string.setup_forget_chats_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.forgetConversations()
                        forgetting = false
                    },
                ) { ButtonLabel(stringResource(R.string.setup_forget_chats_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { forgetting = false }) {
                    ButtonLabel(stringResource(R.string.setup_forget_chats_dismiss))
                }
            },
        )
    }
}

@Composable
private fun ShizukuCard(store: Store, state: ShizukuBackend.State) {
    val ctx = LocalContext.current
    SectionTitle(
        stringResource(R.string.transport_shizuku),
        trailing = {
            val pill = when (state) {
                ShizukuBackend.State.CONNECTED -> R.string.shizuku_state_connected
                ShizukuBackend.State.CONNECTING -> R.string.shizuku_state_connecting
                ShizukuBackend.State.NEEDS_PERMISSION -> R.string.shizuku_state_needs_permission
                ShizukuBackend.State.NOT_RUNNING -> R.string.shizuku_state_not_running
                ShizukuBackend.State.NOT_INSTALLED -> R.string.shizuku_state_not_installed
                ShizukuBackend.State.FAILED -> R.string.shizuku_state_failed
            }
            LivePill(stringResource(pill), state == ShizukuBackend.State.CONNECTED)
        },
    )

    Caption(stringResource(R.string.shizuku_reattach_note))

    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(tween(160)).togetherWith(fadeOut(tween(100))) },
        label = "shizukuState",
    ) { s ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (s) {
                ShizukuBackend.State.NOT_INSTALLED -> {
                    Caption(stringResource(R.string.shizuku_not_installed_body))
                    Button(onClick = { openShizukuListing(ctx) }) {
                        ButtonLabel(stringResource(R.string.shizuku_get))
                    }
                }
                ShizukuBackend.State.NOT_RUNNING -> {
                    Caption(stringResource(R.string.shizuku_not_running_body))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { openShizuku(ctx) }) {
                            ButtonLabel(stringResource(R.string.shizuku_open))
                        }
                        TextButton(onClick = { store.shizuku.refresh() }) {
                            ButtonLabel(stringResource(R.string.shizuku_check_again))
                        }
                    }
                }
                ShizukuBackend.State.NEEDS_PERMISSION -> {
                    Caption(stringResource(R.string.shizuku_needs_permission_body))
                    Button(onClick = { store.shizuku.requestPermission() }) {
                        ButtonLabel(stringResource(R.string.shizuku_request_access))
                    }
                }
                ShizukuBackend.State.CONNECTED -> {
                    Caption(stringResource(R.string.shizuku_connected_body))
                    TextButton(onClick = { store.disconnectShizuku() }) {
                        ButtonLabel(stringResource(R.string.shizuku_disconnect))
                    }
                }
                else -> {
                    Caption(
                        store.shizuku.errorRes()?.let { stringResource(it) }
                            ?: store.shizuku.errorText()
                            ?: stringResource(R.string.shizuku_unreachable)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { store.shizuku.refresh() }) {
                            ButtonLabel(stringResource(R.string.shizuku_retry))
                        }
                        TextButton(onClick = { openShizuku(ctx) }) {
                            ButtonLabel(stringResource(R.string.shizuku_open))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbCard(ctx: Context) {
    SectionTitle(stringResource(R.string.adb_title))
    Caption(stringResource(R.string.adb_body))
    Text(
        ADB_COMMAND,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
    )
    Caption(stringResource(R.string.adb_shells_note))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { copy(ctx, ADB_COMMAND, R.string.adb_copied) }) {
            ButtonLabel(stringResource(R.string.adb_copy))
        }
        TextButton(onClick = { copy(ctx, ADB_COMMAND_CMD, R.string.adb_copied_cmd) }) {
            ButtonLabel(stringResource(R.string.adb_copy_cmd))
        }
    }
    Caption(stringResource(R.string.adb_verify_note))
    TextButton(onClick = { share(ctx, ADB_COMMAND) }) {
        ButtonLabel(stringResource(R.string.adb_send))
    }
}

private fun copy(ctx: Context, text: String, @StringRes toast: Int) {
    ctx.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("hilight", text))
    Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
}

private fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.adb_share_title)))
}

private fun openShizuku(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage(ShizukuBackend.SHIZUKU_PKG)
    if (launch != null) ctx.startActivity(launch) else openShizukuListing(ctx)
}

private fun openShizukuListing(ctx: Context) {
    val uri = Uri.parse("https://shizuku.rikka.app/")
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { Toast.makeText(ctx, R.string.setup_no_browser, Toast.LENGTH_SHORT).show() }
}

private fun openExternalUrl(ctx: Context, pageUrl: String) {
    val uri = Uri.parse(pageUrl)
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { Toast.makeText(ctx, R.string.setup_no_browser, Toast.LENGTH_SHORT).show() }
}

private fun postSelfTestNotification(ctx: Context) {
    val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
    nm.createNotificationChannel(
        android.app.NotificationChannel(
            "selftest",
            ctx.getString(R.string.setup_selftest_channel),
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        )
    )
    nm.notify(
        42,
        android.app.Notification.Builder(ctx, "selftest")
            .setContentTitle(ctx.getString(R.string.setup_selftest_title))
            .setContentText(ctx.getString(R.string.setup_selftest_body))
            .setSmallIcon(R.drawable.hilight_logo)
            .setAutoCancel(true)
            .build()
    )
}

fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun pickTime(ctx: Context, currentMinutes: Int, onPicked: (Int) -> Unit) {
    android.app.TimePickerDialog(
        ctx,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        currentMinutes / 60,
        currentMinutes % 60,
        android.text.format.DateFormat.is24HourFormat(ctx),
    ).show()
}

private fun hasNotificationAccess(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        ?: return false
    return flat.contains(ctx.packageName)
}