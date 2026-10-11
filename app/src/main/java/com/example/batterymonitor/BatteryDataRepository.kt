package com.example.batterymonitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BatteryDataRepository {
    private val _currentFlow = MutableStateFlow<List<Float>>(emptyList())
    val currentFlow: StateFlow<List<Float>> = _currentFlow.asStateFlow()
    
    val isTracking = MutableStateFlow(false)

    fun addCurrentValue(value: Float) {
        val currentList = _currentFlow.value.toMutableList()
        currentList.add(value)
        // Belleği korumak için son 60 saniyeyi (1 dakikalık veri) tutuyoruz
        if (currentList.size > 60) {
            currentList.removeAt(0)
        }
        _currentFlow.value = currentList
    }

    fun clearData() {
        _currentFlow.value = emptyList()
    }
}
