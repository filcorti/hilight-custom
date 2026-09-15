package com.hilight.studio

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun RemindersScreen(store: Store) {
    val context = LocalContext.current
    var selectedIntervalHours by remember { mutableIntStateOf(2) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Promemoria LED",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Attiva un ciclo di illuminazione a intervalli regolari per pause o avvisi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ripeti ogni:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(1, 2, 3, 4).forEach { hours ->
                        if (selectedIntervalHours == hours) {
                            Button(onClick = { selectedIntervalHours = hours }) {
                                Text("${hours}h")
                            }
                        } else {
                            OutlinedButton(onClick = { selectedIntervalHours = hours }) {
                                Text("${hours}h")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                ReminderScheduler.scheduleInterval(
                    context = context,
                    intervalHours = selectedIntervalHours,
                    pattern = Pattern.PULSE,
                    color = 0xFF00E5FF.toInt()
                )
                Toast.makeText(context, "Promemoria impostato ogni $selectedIntervalHours ore", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Attiva promemoria")
        }

        OutlinedButton(
            onClick = {
                ReminderScheduler.cancelReminders(context)
                Toast.makeText(context, "Promemoria annullati", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disattiva tutti i promemoria")
        }
    }
}