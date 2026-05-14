package com.example.android

import android.app.Application
import com.mapbox.common.MapboxOptions

/**
 * Application entry point. Sets the runtime Mapbox access token before any
 * `MapView` / `MapboxMap` is constructed, mirroring the iOS `AppConfig`
 * `mapboxAccessToken` setup.
 */
class NeighborlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPBOX_ACCESS_TOKEN.isNotBlank()) {
            MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        }
    }
}
