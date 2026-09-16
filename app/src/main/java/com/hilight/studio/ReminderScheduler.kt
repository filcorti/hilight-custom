package com.hilight.studio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val REQUEST_CODE = 4444

    fun scheduleReminderRule(context: Context, intervalMinutes: Int, rule: AppRule) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val safeMinutes = intervalMinutes.coerceAtLeast(1)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("is_active", true)
            putInt("active_interval_minutes", safeMinutes)
            putString("saved_rule_pattern", rule.pattern.name)
            putInt("saved_rule_color", rule.color)
            putInt("saved_rule_speed", rule.speedMs)
            putFloat("saved_rule_brightness", rule.brightness)
            putInt("saved_rule_duration", rule.durationMs)
            putBoolean("saved_rule_screen_off", rule.onlyWhenScreenOff)
            putBoolean("saved_rule_face_down", rule.onlyWhenFaceDown)
        }

        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = safeMinutes * 60 * 1000L
        val triggerAt = System.currentTimeMillis() + intervalMillis

        try {
            val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }

            if (canSchedule) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Permesso esatto non concesso; imposto allarme con tolleranza standard", se)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella pianificazione del promemoria", e)
        }
    }

    fun cancelReminders(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        pi.cancel()

        context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("is_active", false)
        }
    }
}