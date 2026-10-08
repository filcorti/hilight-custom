package com.hilight.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun DeviceSignalsSection(store: Store) {
    val settings by store.deviceSignals.settings.collectAsStateWithLifecycle()
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.device_signals_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.device_signals_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToggleRow(stringResource(R.string.device_signals_charging), settings.chargingEnabled) {
                store.deviceSignals.updateSettings { s -> s.copy(chargingEnabled = it) }
            }
            if (settings.chargingEnabled) {
                Text(
                    text = stringResource(R.string.device_signals_charging_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPicker(settings.chargingColor, { color -> store.deviceSignals.updateSettings { it.copy(chargingColor = color) } }, stringResource(R.string.device_signals_charging_color))
                ColorPicker(settings.chargedColor, { color -> store.deviceSignals.updateSettings { it.copy(chargedColor = color) } }, stringResource(R.string.device_signals_charged_color))
                Text(
                    text = stringResource(R.string.device_signals_full_percent, settings.fullPercent),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = settings.fullPercent.toFloat(),
                    onValueChange = { percent ->
                        store.deviceSignals.updateSettings { it.copy(fullPercent = percent.roundToInt()) }
                    },
                    valueRange = 1f..100f,
                    steps = 98,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ToggleRow(stringResource(R.string.device_signals_dnd), settings.dndEnabled) {
                store.deviceSignals.updateSettings { s -> s.copy(dndEnabled = it) }
            }
            if (settings.dndEnabled) {
                Text(
                    text = stringResource(R.string.device_signals_dnd_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPicker(settings.dndColor, { color -> store.deviceSignals.updateSettings { it.copy(dndColor = color) } })
            }
            ToggleRow(stringResource(R.string.device_signals_calls), settings.callsEnabled) {
                store.deviceSignals.updateSettings { s -> s.copy(callsEnabled = it) }
            }
            if (settings.callsEnabled) {
                Text(
                    text = stringResource(R.string.device_signals_calls_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ColorPicker(settings.callColor, { color -> store.deviceSignals.updateSettings { it.copy(callColor = color) } })
            }
        }
    }
}
