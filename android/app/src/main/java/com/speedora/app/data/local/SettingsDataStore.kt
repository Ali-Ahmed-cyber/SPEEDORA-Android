package com.speedora.app.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.speedoraPrefs: DataStore<Preferences> by preferencesDataStore(name = "speedora_settings")

data class SpeedoraSettings(
    val languageCode: String = "ar",
    val darkTheme: Boolean = false,
    val animationIntensity: Float = 1.0f,
    val reducedMotion: Boolean = false,
    val batterySavingMode: Boolean = false,
    val hapticFeedback: Boolean = true,
    val soundEffects: Boolean = true,
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    val customDownloadTreeUri: String? = null,
    val requireIncomingApproval: Boolean = true,
    val allowTrustedAutoAccept: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ANIMATION_INTENSITY = floatPreferencesKey("animation_intensity")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saving_mode")
        val HAPTICS = booleanPreferencesKey("haptic_feedback")
        val SOUNDS = booleanPreferencesKey("sound_effects")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
        val REQUIRE_INCOMING_APPROVAL = booleanPreferencesKey("require_incoming_approval")
        val ALLOW_TRUSTED_AUTO = booleanPreferencesKey("allow_trusted_auto_accept")
    }

    val settingsFlow: Flow<SpeedoraSettings> = context.speedoraPrefs.data.map { prefs ->
        SpeedoraSettings(
            languageCode = prefs[Keys.LANGUAGE] ?: "ar",
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            animationIntensity = prefs[Keys.ANIMATION_INTENSITY] ?: 1.0f,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            batterySavingMode = prefs[Keys.BATTERY_SAVER] ?: false,
            hapticFeedback = prefs[Keys.HAPTICS] ?: true,
            soundEffects = prefs[Keys.SOUNDS] ?: true,
            deviceName = prefs[Keys.DEVICE_NAME] ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            customDownloadTreeUri = prefs[Keys.DOWNLOAD_TREE_URI],
            requireIncomingApproval = prefs[Keys.REQUIRE_INCOMING_APPROVAL] ?: true,
            allowTrustedAutoAccept = prefs[Keys.ALLOW_TRUSTED_AUTO] ?: false
        )
    }

    suspend fun setLanguage(code: String) = context.speedoraPrefs.edit { it[Keys.LANGUAGE] = code }
    suspend fun setDarkTheme(enabled: Boolean) = context.speedoraPrefs.edit { it[Keys.DARK_THEME] = enabled }
    suspend fun setAnimationIntensity(value: Float) = context.speedoraPrefs.edit { it[Keys.ANIMATION_INTENSITY] = value.coerceIn(0f, 1f) }
    suspend fun setReducedMotion(enabled: Boolean) = context.speedoraPrefs.edit { it[Keys.REDUCED_MOTION] = enabled }
    suspend fun setBatterySavingMode(enabled: Boolean) = context.speedoraPrefs.edit { it[Keys.BATTERY_SAVER] = enabled }
    suspend fun setHapticFeedback(enabled: Boolean) = context.speedoraPrefs.edit { it[Keys.HAPTICS] = enabled }
    suspend fun setSoundEffects(enabled: Boolean) = context.speedoraPrefs.edit { it[Keys.SOUNDS] = enabled }
    suspend fun setDeviceName(name: String) = context.speedoraPrefs.edit {
        val trimmed = name.trim().take(48)
        if (trimmed.isNotEmpty()) it[Keys.DEVICE_NAME] = trimmed
    }
    suspend fun setCustomDownloadTreeUri(uri: String?) = context.speedoraPrefs.edit {
        if (uri == null) it.remove(Keys.DOWNLOAD_TREE_URI) else it[Keys.DOWNLOAD_TREE_URI] = uri
    }
    suspend fun setRequireIncomingApproval(required: Boolean) = context.speedoraPrefs.edit {
        it[Keys.REQUIRE_INCOMING_APPROVAL] = required
    }
    suspend fun setAllowTrustedAutoAccept(allowed: Boolean) = context.speedoraPrefs.edit {
        it[Keys.ALLOW_TRUSTED_AUTO] = allowed
    }
}
