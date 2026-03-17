package com.smartexpense.tracker.service.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device battery status and provides real-time updates.
 * Tracks battery level, charging state, temperature, and power-saving mode
 * so the app can adapt its behaviour (e.g. defer AI inference when battery is low).
 */
class BatteryMonitorService(private val context: Context) {

    data class BatteryState(
        /** Battery level as a percentage (0–100). */
        val level: Int = -1,
        /** True when the device is connected to a charger. */
        val isCharging: Boolean = false,
        /** Charging source description (e.g. "USB", "AC", "Wireless", "Not charging"). */
        val chargingSource: String = "Unknown",
        /** Battery temperature in degrees Celsius. */
        val temperatureCelsius: Float = 0f,
        /** Battery voltage in volts. */
        val voltage: Float = 0f,
        /** Battery health description (e.g. "Good", "Overheat", "Dead"). */
        val health: String = "Unknown",
        /** Battery technology (e.g. "Li-ion"). */
        val technology: String = "Unknown",
        /** True when the OS power-saving / battery-saver mode is active. */
        val isPowerSaveMode: Boolean = false,
        /** Timestamp of the last update (epoch millis). */
        val lastUpdated: Long = 0
    ) {
        /** True when battery level is at or below 15%. */
        val isLow: Boolean get() = level in 0..15

        /** True when battery level is at or below 5%. */
        val isCritical: Boolean get() = level in 0..5

        /** Human-readable summary for display in the UI. */
        val summary: String
            get() = buildString {
                if (level >= 0) append("$level%")
                if (isCharging) append(" · $chargingSource")
                if (isPowerSaveMode) append(" · Power Saver")
            }
    }

    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    /**
     * Starts listening for battery change broadcasts.
     * Call this from Activity.onStart() or Application.onCreate().
     */
    fun startMonitoring() {
        if (receiver != null) return

        // Read initial state from the sticky broadcast
        val initialIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (initialIntent != null) {
            _batteryState.value = parseBatteryIntent(initialIntent)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        _batteryState.value = parseBatteryIntent(intent)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        _batteryState.value = _batteryState.value.copy(
                            isPowerSaveMode = isPowerSaveMode(),
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
    }

    /**
     * Stops listening for battery broadcasts.
     * Call this from Activity.onStop() or when monitoring is no longer needed.
     */
    fun stopMonitoring() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) { /* already unregistered */ }
            receiver = null
        }
    }

    private fun parseBatteryIntent(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (scale > 0) (level * 100) / scale else -1

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingSource = when {
            !isCharging -> "Not charging"
            plugged == BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Unknown"
        }

        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"

        return BatteryState(
            level = pct,
            isCharging = isCharging,
            chargingSource = chargingSource,
            temperatureCelsius = tempRaw / 10f,
            voltage = voltageRaw / 1000f,
            health = health,
            technology = technology,
            isPowerSaveMode = isPowerSaveMode(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun isPowerSaveMode(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }
}
