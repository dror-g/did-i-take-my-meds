package dev.corruptedark.diditakemymeds.settings

import com.chibatching.kotpref.KotprefModel
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.main.SortBy

object AppSettings : KotprefModel(){
    override val kotprefName: String = " AppSettings"

    var sortByString by stringPref(AppSettingsLegacy.sortByString)
    var lastVersionUsed by intPref(AppSettingsLegacy.lastVersionUsed)

    var currentThemeKey by stringPref(
        ThemePrefHelper.defaultThemePrefValue,
        key = R.string.pref_key_app_theme
    )

    var sortBy: SortBy
        get() {
            return SortBy.getByKeyOrDefault(sortByString, SortBy.TIME)
        }
        set(value) {
            sortByString = value.key
        }

    var currentTheme: Int
        get() {
            return ThemePrefHelper.getThemeResIdOrDefault(currentThemeKey)
        }
        private set(value) {
            val prefValue = ThemePrefHelper.getThemePrefValueOrDefault(value)
            currentThemeKey = prefValue
        }

    var isLightTheme: Boolean
        get() {
            return ThemePrefHelper.isLightThemeResId(currentTheme)
        }
        set(value) {
            currentTheme = if (value) {
                ThemePrefHelper.lightThemeResId
            } else {
                ThemePrefHelper.darkThemeResId
            }
        }


}