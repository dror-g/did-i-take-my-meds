package dev.corruptedark.diditakemymeds.settings

import com.chibatching.kotpref.KotprefModel
import dev.corruptedark.diditakemymeds.activities.main.SortBy

object AppSettingsLegacy : KotprefModel() {
    override val kotprefName = "activities.MainActivity.xml"
    var sortByString by stringPref(SortBy.TIME.key, key = "Sort")
    val lastVersionUsed by intPref(0, key = "last_version_used")
}