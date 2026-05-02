package com.example.android.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class UserCoordinates(val latitude: Double, val longitude: Double)

interface LocationHelper {
    fun hasPermission(): Boolean
    suspend fun requestLastKnownLocation(timeoutMs: Long = 3_000L): UserCoordinates?
}

class FusedLocationHelper(
    private val context: Context,
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) : LocationHelper {
    override fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @Suppress("MissingPermission")
    override suspend fun requestLastKnownLocation(timeoutMs: Long): UserCoordinates? {
        if (!hasPermission()) return null
        return runCatching {
            withTimeoutOrNull(timeoutMs) {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(60_000L)
                    .build()
                val location = client.getCurrentLocation(request, null).await() ?: return@withTimeoutOrNull null
                UserCoordinates(location.latitude, location.longitude)
            }
        }.getOrNull()
    }
}
