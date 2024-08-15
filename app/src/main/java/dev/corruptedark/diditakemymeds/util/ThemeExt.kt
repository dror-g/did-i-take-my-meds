package dev.corruptedark.diditakemymeds.util

import android.app.Activity
import dev.corruptedark.diditakemymeds.settings.AppSettings

fun Activity.applyThemeFromPreferences() {
    val appTheme = AppSettings.currentTheme
    setTheme(appTheme)
}