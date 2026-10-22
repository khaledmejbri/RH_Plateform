package com.example.rh_mobile_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "rh_connect/location"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isLocationEnabled" -> {
                        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        result.success(
                            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
                        )
                    }
                    "getCurrentPosition" -> obtainPosition(result)
                    else -> result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "rh_connect/device")
            .setMethodCallHandler { call, result ->
                if (call.method == "isEmulator") {
                    result.success(isEmulator())
                } else {
                    result.notImplemented()
                }
            }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val product = Build.PRODUCT
        val hardware = Build.HARDWARE
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("Emulator") ||
            model.contains("Android SDK built for") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            product.contains("vbox")
    }

    private fun obtainPosition(result: MethodChannel.Result) {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            result.error("PERMISSION", "Localisation non autorisée", null)
            return
        }

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            result.error("DISABLED", "GPS désactivé", null)
            return
        }

        val last = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time }

        // Dernière position récente (< 2 min) : réponse immédiate.
        if (last != null && System.currentTimeMillis() - last.time < 120_000) {
            result.success(locationMap(last))
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var delivered = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (delivered) return
                delivered = true
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {
                }
                result.success(locationMap(location))
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            mainHandler.postDelayed({
                if (delivered) return@postDelayed
                delivered = true
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {
                }
                if (last != null) {
                    result.success(locationMap(last))
                } else {
                    result.error(
                        "TIMEOUT",
                        "Impossible d’obtenir la position GPS. Réessayez à l’extérieur.",
                        null,
                    )
                }
            }, 20_000)
        } catch (e: SecurityException) {
            result.error("PERMISSION", e.message, null)
        } catch (e: Exception) {
            result.error("ERROR", e.message, null)
        }
    }

    private fun locationMap(location: Location): Map<String, Any?> =
        mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracyMeters" to if (location.hasAccuracy()) location.accuracy.toDouble() else null,
        )
}
