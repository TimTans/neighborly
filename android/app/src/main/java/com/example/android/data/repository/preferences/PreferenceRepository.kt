package com.example.android.data.repository.preferences

interface PreferenceRepository {
    fun loadPreferences(): PreferenceState
    fun savePreferences(preferences: PreferenceState)
}
