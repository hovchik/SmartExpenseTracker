package com.flowsense.app.util

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
 *
 * Also maintains a SharedPreferences cache so that background components
 * (BroadcastReceiver, NotificationListenerService) can fall back to
 * the most recent foreground fix when the system cache is empty.
 */
object LocationProvider {

    data class LatLng(val latitude: Double, val longitude: Double)

    private const val PREFS_NAME = "location_cache"
    private const val KEY_LAT = "cached_lat"
    private const val KEY_LNG = "cached_lng"
    private const val KEY_TIME = "cached_time"

    /** Maximum age (ms) for the cached location before it is considered stale. */
    private const val MAX_CACHE_AGE_MS = 30 * 60 * 1000L // 30 minutes

    /**
     * Returns the best last-known location, or null if unavailable.
     * Tries the system LocationManager first, then falls back to a
     * SharedPreferences cache written by [cacheLocation].
     */
    fun getLastKnownLocation(context: Context): LatLng? {
        val appCtx = context.applicationContext
        val live = getLiveLocation(appCtx)
        if (live != null) {
            cacheLocation(appCtx, live)
            return live
        }
        return getCachedLocation(appCtx)
    }

    /**
     * Persist [location] so that background components can read it later.
     * Call this whenever the app obtains a fresh fix in the foreground.
     */
    fun cacheLocation(context: Context, location: LatLng) {
        if (!isValidLatLng(location.latitude, location.longitude)) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAT, location.latitude.toBits())
            .putLong(KEY_LNG, location.longitude.toBits())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * Read the cached location from SharedPreferences.
     * Returns null if no cache exists or the cache is older than [MAX_CACHE_AGE_MS].
     */
    fun getCachedLocation(context: Context): LatLng? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_TIME, 0L)
        if (time == 0L || System.currentTimeMillis() - time > MAX_CACHE_AGE_MS) return null
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LNG)) return null
        val lat = Double.fromBits(prefs.getLong(KEY_LAT, 0L))
        val lng = Double.fromBits(prefs.getLong(KEY_LNG, 0L))
        if (!isValidLatLng(lat, lng)) return null
        return LatLng(lat, lng)
    }

    private fun isValidLatLng(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0
    }

    /**
     * Query the system [LocationManager] for the best last-known fix.
     * Returns null when location permissions are not granted or no provider has a fix.
     */
    private fun getLiveLocation(context: Context): LatLng? {
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

        return best
            ?.takeIf { isValidLatLng(it.latitude, it.longitude) }
            ?.let { LatLng(it.latitude, it.longitude) }
    }
}
