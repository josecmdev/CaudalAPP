package com.example.caudalapp

import android.content.Context

enum class AppTheme(val label: String) {
    SYSTEM("Automático"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val gpsIntervalSeconds: Int = 1,
    val automaticMapFollow: Boolean = true,
    val hapticFeedback: Boolean = true,
)

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("caudal-settings-v1", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        theme = runCatching {
            AppTheme.valueOf(preferences.getString("theme", AppTheme.SYSTEM.name).orEmpty())
        }.getOrDefault(AppTheme.SYSTEM),
        gpsIntervalSeconds = preferences.getInt("gps_interval_seconds", 1).takeIf { it in setOf(1, 3, 5, 10) } ?: 1,
        automaticMapFollow = preferences.getBoolean("automatic_map_follow", true),
        hapticFeedback = preferences.getBoolean("haptic_feedback", true),
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString("theme", settings.theme.name)
            .putInt("gps_interval_seconds", settings.gpsIntervalSeconds)
            .putBoolean("automatic_map_follow", settings.automaticMapFollow)
            .putBoolean("haptic_feedback", settings.hapticFeedback)
            .apply()
    }
}
