package dev.corruptedark.diditakemymeds.settings

import dev.corruptedark.diditakemymeds.R

object ThemePrefHelper {
    const val THEME_LIGHT = "Theme.DidITakeMyMeds.Light"
    const val THEME_DARK = "Theme.DidITakeMyMeds"
    private val themePrefToIdMap = mapOf(
        THEME_LIGHT to R.style.Theme_DidITakeMyMeds_Light,
        THEME_DARK to R.style.Theme_DidITakeMyMeds,
    )
    private val themeIdToNameMap = themePrefToIdMap.map { it.value to it.key }.toMap()
    const val defaultThemePrefValue = THEME_DARK

    fun isLightThemeResId(resId: Int) = themePrefToIdMap[THEME_LIGHT] == resId
    fun isDarkThemeResId(resId: Int) = themePrefToIdMap[THEME_DARK] == resId

    val lightThemeResId = themePrefToIdMap[THEME_LIGHT]!!
    val darkThemeResId = themePrefToIdMap[THEME_DARK]!!


    private fun getDefaultThemeResId() = themePrefToIdMap[defaultThemePrefValue]!!
    fun getThemeResIdOrDefault(themePrefValue: String) =
        themePrefToIdMap[themePrefValue] ?: getDefaultThemeResId()

    fun getThemePrefValueOrDefault(themeResId: Int) =
        themeIdToNameMap[themeResId] ?: defaultThemePrefValue

}