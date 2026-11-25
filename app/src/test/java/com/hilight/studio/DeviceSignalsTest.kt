package com.hilight.studio

import org.junit.Assert.*
import org.junit.Test

class DeviceSignalsTest {
    @Test fun existingInstallationsDoNotEnableSignals() {
        val settings = DeviceSignalSettings()
        assertFalse(settings.chargingEnabled)
        assertTrue(settings.faceDownBatteryEnabled)
        assertFalse(settings.callsEnabled)
    }

    @Test fun chargingSwitchesAtConfiguredThreshold() {
        val settings = DeviceSignalSettings(fullPercent = 80, chargingColor = 123, chargedColor = 456)
        assertEquals(Pattern.BLINK, chargingSignalLook(settings, 79).pattern)
        assertEquals(123, chargingSignalLook(settings, 79).color)
        assertEquals(Pattern.SOLID, chargingSignalLook(settings, 80).pattern)
        assertEquals(456, chargingSignalLook(settings, 80).color)
        assertEquals(Pattern.SOLID, chargingSignalLook(settings, 100).pattern)
    }
}
