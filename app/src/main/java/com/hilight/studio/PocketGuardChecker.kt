package com.hilight.studio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

object PocketGuardChecker {

    fun isInPocket(context: Context): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        val proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var isNear = false
        var accelX = 0f
        var accelY = 0f
        var accelZ = 9.81f
        var hasProx = false

        val latch = CountDownLatch(if (proxSensor != null && accelSensor != null) 2 else if (proxSensor != null || accelSensor != null) 1 else 0)
        if (latch.count == 0L) return false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> {
                        val maxRange = proxSensor?.maximumRange ?: 5f
                        isNear = event.values[0] < maxRange && event.values[0] < 5f
                        hasProx = true
                        latch.countDown()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelX = event.values[0]
                        accelY = event.values[1]
                        accelZ = event.values[2]
                        latch.countDown()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        try {
            proxSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) }
            accelSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST) }
            latch.await(80, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        } finally {
            sensorManager.unregisterListener(listener)
        }

        if (!hasProx) return false

        val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        val deviationFromGravity = abs(magnitude - 9.81f)
        val tiltX = abs(accelX)
        val tiltY = abs(accelY)

        val isFlatOnTable = tiltX < 2.5f && tiltY < 2.5f && deviationFromGravity < 1.0f

        return isNear && !isFlatOnTable
    }
}
