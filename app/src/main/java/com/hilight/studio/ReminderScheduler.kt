package com.hilight.studio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object ReminderScheduler {

    private const val INTERVAL_REQUEST_CODE = 3001
    private const val FIXED_TIME_REQUEST_CODE = 3002

    /**
     * Pianifica un allarme a intervalli regolari (es. ogni 1, 2, 3 ore)
     */
    fun scheduleInterval(
        context: Context,
        intervalHours: Int,
        pattern: Pattern = Pattern.PULSE,
        color: Int = 0xFF00E5FF.toInt()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("PATTERN_KEY", pattern.key)
            putExtra("COLOR", color)
            putExtra("DURATION_MS", 10_000)
            putExtra("SPEED_MS", 800)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            INTERVAL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMillis = intervalHours * 60 * 60 * 1000L
        val firstTriggerAt = System.currentTimeMillis() + intervalMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTriggerAt,
            intervalMillis,
            pendingIntent
        )
    }

    /**
     * Pianifica un allarme ad orario specifico (es. alle 14:30)
     */
    fun scheduleFixedTime(
        context: Context,
        hour: Int,
        minute: Int,
        pattern: Pattern = Pattern.PULSE,
        color: Int = 0xFF00E5FF.toInt()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("PATTERN_KEY", pattern.key)
            putExtra("COLOR", color)
            putExtra("DURATION_MS", 10_000)
            putExtra("SPEED_MS", 800)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            FIXED_TIME_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    /**
     * Cancella i promemoria impostati
     */
    fun cancelReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)

        val pendingInterval = PendingIntent.getBroadcast(
            context,
            INTERVAL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pendingFixed = PendingIntent.getBroadcast(
            context,
            FIXED_TIME_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingInterval)
        alarmManager.cancel(pendingFixed)
    }
}