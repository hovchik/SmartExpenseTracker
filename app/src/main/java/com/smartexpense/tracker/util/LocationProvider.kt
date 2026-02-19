package com.smartexpense.tracker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Lightweight helper that returns the device's last-known location
 * using the Android [LocationManager] (no Google Play Services needed).
 *
 * Checks GPS → Network providers in that order. Returns null when
 * location permissions are not granted or no provider has a fix.
 */
object LocationProvider {

    data class LatLng(val latitude: Double, val longitude: Double)

    /**
     * Returns the best last-known location, or null if unavailable.
     * Must be called with fine/coarse location permission already granted.
     */
    fun getLastKnownLocation(context: Context): LatLng? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        var best: Location? = null
        for (provider in providers) {
            if (!lm.isProviderEnabled(provider)) continue
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
            if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                best = loc
            }
        }

        return best?.let { LatLng(it.latitude, it.longitude) }
    }
}
