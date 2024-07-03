package dev.corruptedark.diditakemymeds.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.util.broadcastIntentFromIntent
import dev.corruptedark.diditakemymeds.util.notifications.ActionReceiver
import dev.corruptedark.diditakemymeds.util.timeSinceLastDoseString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


private const val PREFS_NAME = "dev.corruptedark.diditakemymeds.SimpleSingleMedWidget"
private const val PREF_PREFIX_KEY = "appwidget_"
private const val PREF_KEY_LAYOUT = "_layout"


internal fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
) {
    val medId = loadMedIdPref(context, appWidgetId)
    val layoutId = getLayoutPref(context, appWidgetId)
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, layoutId)

    GlobalScope.launch(Dispatchers.IO) {
        val medication: Medication? = if (medId != Medication.INVALID_MED_ID) {
            medicationDao(context).get(medId)
        } else {
            null
        }

        medication?.apply {
            val name = medication.name
            val timeSinceText = medication.timeSinceLastDoseString(context)

            val tookMedIntent = Intent(context, ActionReceiver::class.java).apply {
                action = ActionReceiver.TOOK_MED_ACTION
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            val tookMedPendingIntent = context.broadcastIntentFromIntent(
                    medication.id.toInt(), tookMedIntent
            )
            val buttonText = if (medication.closestDoseAlreadyTaken()) {
                context.getString(R.string.took_this_already)
            } else {
                context.getString(R.string.i_just_took_it)
            }

            SimpleSingleMedWidgetBase.mainScope.launch {
                views.setTextViewText(R.id.name_label, name)
                views.setTextViewText(R.id.time_since_dose_label, timeSinceText)
                views.setOnClickPendingIntent(R.id.just_took_it_button, tookMedPendingIntent)
                views.setTextViewText(R.id.just_took_it_button, buttonText)

                // Instruct the widget manager to update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

internal fun saveLayoutPref(context: Context, appWidgetId: Int, layoutId: Int) {
    val prefName = getLayoutPrefKey(appWidgetId)
    getSharedPreferences(context).edit().putInt(prefName, layoutId).apply()
}

internal fun deleteLayoutPref(context: Context, appWidgetId: Int) {
    val prefName = getLayoutPrefKey(appWidgetId)
    getSharedPreferences(context).edit().remove(prefName).apply()
}

internal fun getLayoutPref(context: Context, appWidgetId: Int): Int {
    val prefName = getLayoutPrefKey(appWidgetId)
    return getSharedPreferences(context)
            .getInt(prefName, R.layout.simple_single_med_widget_dark)
}

internal fun saveMedIdPref(context: Context, appWidgetId: Int, medId: Long) {
    val prefName = getMedIdPrefKey(context, appWidgetId)
    getSharedPreferences(context).edit().putLong(prefName, medId).apply()
}

internal fun loadMedIdPref(context: Context, appWidgetId: Int): Long {
    val prefName = getMedIdPrefKey(context, appWidgetId)
    return getSharedPreferences(context).getLong(prefName, Medication.INVALID_MED_ID)
}

internal fun deleteMedIdPref(context: Context, appWidgetId: Int) {
    val prefName = getMedIdPrefKey(context, appWidgetId)
    getSharedPreferences(context).edit().remove(prefName).apply()
}

private fun getMedIdPrefKey(context: Context, appWidgetId: Int): String {
    val prefKey = context.applicationContext.getString(R.string.med_id_key)
    return "$PREF_PREFIX_KEY$appWidgetId$prefKey"
}

private fun getLayoutPrefKey(appWidgetId: Int): String {
    return "$PREF_PREFIX_KEY$appWidgetId$PREF_KEY_LAYOUT"
}

private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
