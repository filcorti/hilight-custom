package com.hilight.studio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class DeviceStateSignal {
    data class ScreenState(val isScreenOff: Boolean) : DeviceStateSignal()
    data class OrientationState(val isFaceDown: Boolean) : DeviceStateSignal()
    data class PowerSaveState(val isBatterySaver: Boolean) : DeviceStateSignal()
}

object BoundedSignalDispatcher {
    private val _signals = MutableSharedFlow<DeviceStateSignal>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signals: SharedFlow<DeviceStateSignal> = _signals.asSharedFlow()

    fun emit(signal: DeviceStateSignal) {
        _signals.tryEmit(signal)
    }
}