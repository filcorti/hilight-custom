package com.hilight.studio

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun RemindersScreen(store: Store) {
    val context = LocalContext.current
    val launchPreview = rememberPreviewLauncher(store)
    val prefs = remember { context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE) }

    var isReminderActive by remember {
        mutableStateOf(prefs.getBoolean("is_active", false))
    }
    var savedIntervalHours by remember {
        mutableIntStateOf(prefs.getInt("active_interval", 1))
    }

    val intervals = listOf(1, 2, 3, 4)
    var selectedHours by remember { mutableIntStateOf(savedIntervalHours) }

    var reminderRule by remember {
        val patternName = prefs.getString("saved_rule_pattern", Pattern.PULSE.name) ?: Pattern.PULSE.name
        val pattern = runCatching { Pattern.valueOf(patternName) }.getOrDefault(Pattern.PULSE)
        val color = prefs.getInt("saved_rule_color", 0xFF00E5FF.toInt())
        val speed = prefs.getInt("saved_rule_speed", 1000)
        val bright = prefs.getFloat("saved_rule_brightness", 1.0f)
        val duration = prefs.getInt("saved_rule_duration", 4000)
        val screenOff = prefs.getBoolean("saved_rule_screen_off", false)
        val faceDown = prefs.getBoolean("saved_rule_face_down", false)

        mutableStateOf(
            AppRule(
                pkg = "com.hilight.studio.reminder",
                label = "Promemoria",
                pattern = pattern,
                color = color,
                speedMs = speed,
                brightness = bright,
                durationMs = duration,
                onlyWhenScreenOff = screenOff,
                onlyWhenFaceDown = faceDown
            )
        )
    }

    // Niente più verticalScroll qui: eredita lo scroll naturale della schermata principale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configura Promemoria", style = MaterialTheme.typography.headlineMedium)

        // 1. STATO
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isReminderActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Stato Promemoria", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                if (isReminderActive) {
                    Text(
                        text = "● ATTIVO — Ogni $savedIntervalHours ${if (savedIntervalHours == 1) "ora" else "ore"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "○ NESSUN PROMEMORIA ATTIVO",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }

        // 2. ANTEPRIMA STRISCIA LED
        LedStrip(
            pattern = reminderRule.pattern,
            cfg = reminderRule.effectiveLook(),
            active = true,
            heightDp = 38
        )

        // 3. SELEZIONE CADENZA
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Intervallo di ripetizione", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervals.forEach { hours ->
                        FilterChip(
                            selected = selectedHours == hours,
                            onClick = { selectedHours = hours },
                            label = { Text("${hours}h") }
                        )
                    }
                }
            }
        }

        // 4. CAROSELLO DEI PATTERN NATIVI
        PatternCarousel(
            selected = reminderRule.pattern,
            options = Pattern.entries.filter { it != Pattern.OFF && it != Pattern.CUSTOM },
            onSelect = { reminderRule = reminderRule.copy(pattern = it) }
        )

        // 5. COLOR PICKER
        ColorPicker(
            reminderRule.color,
            { reminderRule = reminderRule.copy(color = it) }
        )

        // 6. TEMPO PER CICLO
        if (reminderRule.pattern.usesSpeed) {
            PixelSlider(
                label = stringResource(R.string.rules_time_per_cycle),
                value = reminderRule.speedMs.toFloat(),
                range = 150f..5000f,
                onChange = { reminderRule = reminderRule.copy(speedMs = it.toInt()) },
                typeInSeconds = true
            ) { formatDuration(it.toInt()) }
        }

        // 7. LUMINOSITÀ
        PixelSlider(
            label = stringResource(R.string.rules_brightness),
            value = reminderRule.brightness,
            range = 0.05f..1f,
            onChange = { reminderRule = reminderRule.copy(brightness = it) }
        ) { stringResource(R.string.common_percent, (it * 100).toInt()) }

        // 8. DURATA MOSTRA PER
        GatedDurationSlider(
            label = stringResource(R.string.rules_show_for),
            valueMs = reminderRule.durationMs,
            minMs = 2_000,
            safeMaxMs = Limits.WARN_ABOVE_MS,
            extendedMaxMs = Limits.RULE_MAX_MS,
            unlockLabel = stringResource(R.string.rules_allow_one_minute),
            warnFirst = stringResource(R.string.rules_duration_warn_first_title) to stringResource(R.string.rules_duration_warn_first_body),
            warnSecond = stringResource(R.string.rules_duration_warn_second_title) to stringResource(R.string.rules_duration_warn_second_body),
            onChange = { reminderRule = reminderRule.copy(durationMs = it) }
        )

        // 9. REGOLE SCHERMO SPENTO E A FACCIA IN GIÙ
        ToggleRow(
            label = stringResource(R.string.rules_only_screen_off),
            checked = reminderRule.onlyWhenScreenOff
        ) {
            reminderRule = reminderRule.copy(onlyWhenScreenOff = it)
        }

        ToggleRow(
            label = stringResource(R.string.rules_only_face_down),
            checked = reminderRule.onlyWhenFaceDown
        ) {
            reminderRule = reminderRule.copy(onlyWhenFaceDown = it)
        }

        // 10. PULSANTE TEST SUI LED FISICI
        FilledTonalButton(
            onClick = {
                launchPreview(
                    reminderRule.pattern,
                    reminderRule.color,
                    reminderRule.speedMs,
                    reminderRule.brightness,
                    reminderRule.durationMs,
                    reminderRule.effectiveLook()
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            ButtonLabel(stringResource(R.string.rules_test_on_leds))
        }

        // 11. ATTIVA / DISATTIVA
        Button(
            onClick = {
                ReminderScheduler.scheduleReminderRule(context, selectedHours, reminderRule)
                isReminderActive = true
                savedIntervalHours = selectedHours
                Toast.makeText(context, "Promemoria attivato!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Attiva promemoria")
        }

        OutlinedButton(
            onClick = {
                ReminderScheduler.cancelReminders(context)
                isReminderActive = false
                Toast.makeText(context, "Promemoria disattivato", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disattiva tutti")
        }
    }
}