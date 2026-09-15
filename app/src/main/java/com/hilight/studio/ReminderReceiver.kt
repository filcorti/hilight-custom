package com.hilight.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val patternKey = intent.getStringExtra("PATTERN_KEY") ?: Pattern.PULSE.key
        val color = intent.getIntExtra("COLOR", 0xFF00E5FF.toInt())
        val durationMs = intent.getIntExtra("DURATION_MS", 10_000)
        val speedMs = intent.getIntExtra("SPEED_MS", 800)

        Log.d("HiLightReminder", "Promemoria scattato: pattern=$patternKey, durata=$durationMs")

        // Recuperiamo lo Store dell'app
        val store = Store.get(context)

        // Se HiLight è disattivato dall'utente, non accendere i LED
        if (!store.enabled.value) return

        // Costruiamo una regola temporanea per il promemoria
        val reminderRule = AppRule(
            pkg = "com.hilight.reminder",
            label = "Promemoria",
            enabled = true,
            trigger = Trigger.SCHEDULED,
            pattern = Pattern.of(patternKey),
            color = color,
            durationMs = durationMs,
            speedMs = speedMs,
            brightness = 1.0f
        )

        // Accendiamo i LED tramite il motore ufficiale di HiLight
        store.fireAlert(reminderRule, owner = "reminder:scheduled")
    }
}