package com.hilight.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return

        val patternName = prefs.getString("saved_rule_pattern", Pattern.PULSE.name) ?: Pattern.PULSE.name
        val pattern = runCatching { Pattern.valueOf(patternName) }.getOrDefault(Pattern.PULSE)
        val color = prefs.getInt("saved_rule_color", 0xFF00E5FF.toInt())
        val speedMs = prefs.getInt("saved_rule_speed", 1000)
        val brightness = prefs.getFloat("saved_rule_brightness", 1.0f)
        val durationMs = prefs.getInt("saved_rule_duration", 4000)
        val onlyScreenOff = prefs.getBoolean("saved_rule_screen_off", false)
        val onlyFaceDown = prefs.getBoolean("saved_rule_face_down", false)

        val rule = AppRule(
            pkg = "com.hilight.studio.reminder",
            label = "Promemoria",
            pattern = pattern,
            color = color,
            speedMs = speedMs,
            brightness = brightness,
            durationMs = durationMs,
            onlyWhenScreenOff = onlyScreenOff,
            onlyWhenFaceDown = onlyFaceDown
        )

        val store = Store.get(context)
        store.fireAlert(rule = rule, owner = "reminder")

        // Riprogramma automaticamente per il ciclo successivo
        val intervalHours = prefs.getInt("active_interval", 1)
        ReminderScheduler.scheduleReminderRule(context, intervalHours, rule)
    }
}