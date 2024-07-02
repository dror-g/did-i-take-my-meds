package dev.corruptedark.diditakemymeds.widgets

import android.content.Context
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.Medication


private const val PREFS_NAME = "dev.corruptedark.diditakemymeds.SimpleSingleMedWidget"
private const val PREF_PREFIX_KEY = "appwidget_"
private const val PREF_KEY_LAYOUT = "_layout"

internal fun saveLayoutPref(context: Context, appWidgetId: Int, layoutId: Int) {
    val prefName = "$PREF_PREFIX_KEY$appWidgetId$PREF_KEY_LAYOUT"
    context.getSharedPreferences(PREFS_NAME, 0).edit().putInt(prefName, layoutId).apply()
}
internal fun deleteLayoutPref(context: Context, appWidgetId: Int) {
    val prefName = "$PREF_PREFIX_KEY$appWidgetId$PREF_KEY_LAYOUT"
    context.getSharedPreferences(PREFS_NAME, 0).edit().remove(prefName).apply()
}

internal fun getLayoutPref(context: Context, appWidgetId: Int): Int {
    val prefName = "$PREF_PREFIX_KEY$appWidgetId$PREF_KEY_LAYOUT"
    return context.getSharedPreferences(PREFS_NAME, 0).getInt(prefName, R.layout.simple_single_med_widget_dark)
}


internal fun saveMedIdPref(context: Context, appWidgetId: Int, medId: Long) {
    val prefKey = context.applicationContext.getString(R.string.med_id_key)
    val prefName = "$PREF_PREFIX_KEY$appWidgetId$prefKey"
    context.getSharedPreferences(PREFS_NAME, 0).edit().putLong(prefName, medId).apply()
}

internal fun loadMedIdPref(context: Context, appWidgetId: Int): Long {
    val prefKey = context.applicationContext.getString(R.string.med_id_key)
    val prefName = "$PREF_PREFIX_KEY$appWidgetId$prefKey"
    return context.getSharedPreferences(PREFS_NAME, 0).getLong(prefName, Medication.INVALID_MED_ID)
}

internal fun deleteMedIdPref(context: Context, appWidgetId: Int) {
    val prefKey = context.applicationContext.getString(R.string.med_id_key)
    val prefName = "$PREF_PREFIX_KEY$appWidgetId$prefKey"
    context.getSharedPreferences(PREFS_NAME, 0).edit().remove(prefName).apply()
}
