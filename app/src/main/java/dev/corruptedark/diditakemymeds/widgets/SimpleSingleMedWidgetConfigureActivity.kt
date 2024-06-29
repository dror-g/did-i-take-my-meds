/*
 * Did I Take My Meds? is a FOSS app to keep track of medications
 * Did I Take My Meds? is designed to help prevent a user from skipping doses and/or overdosing
 *     Copyright (C) 2021  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Did I Take My Meds? is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Did I Take My Meds? is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.diditakemymeds.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import dev.corruptedark.diditakemymeds.databinding.SimpleSingleMedWidgetConfigureBinding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.base.BaseBoundActivity
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.listadapters.MedListAdapter
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.db.medicationTypeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


/**
 * The configuration screen for the [SimpleSingleMedWidget] AppWidget.
 */
class SimpleSingleMedWidgetConfigureActivity : BaseBoundActivity<SimpleSingleMedWidgetConfigureBinding>(SimpleSingleMedWidgetConfigureBinding::class) {
    val context = this
    val mainScope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.title = getString(R.string.choose_a_medication)


        lifecycleScope.launch(Dispatchers.Default) {
            val medListAdapter = MedListAdapter(
                context,
                medicationDao(context).getAllRaw(),
                medicationTypeDao(context).getAllRaw()
            )

            mainScope.launch(Dispatchers.Main) {
                binding.medListView.adapter = medListAdapter
                binding.medListView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
                    val medId = medListAdapter.getItem(position).id

                    saveMedIdPref(context, appWidgetId, medId)

                    // It is the responsibility of the configuration activity to update the app widget
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateAppWidget(context, appWidgetManager, appWidgetId)

                    // Make sure we pass back the original appWidgetId
                    val resultValue = Intent()
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(RESULT_OK, resultValue)
                    finish()
                }
            }
        }


        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
    }

}

private const val PREFS_NAME = "dev.corruptedark.diditakemymeds.SimpleSingleMedWidget"
private const val PREF_PREFIX_KEY = "appwidget_"

internal fun saveMedIdPref(context: Context, appWidgetId: Int, medId: Long) {
    val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
    prefs.putLong(PREF_PREFIX_KEY + appWidgetId + context.applicationContext.getString(R.string.med_id_key), medId)
    prefs.apply()
}

internal fun loadMedIdPref(context: Context, appWidgetId: Int): Long {
    val prefs = context.getSharedPreferences(PREFS_NAME, 0)
    return prefs.getLong(
        PREF_PREFIX_KEY + appWidgetId + context.applicationContext.getString(R.string.med_id_key),
        Medication.INVALID_MED_ID
    )
}

internal fun deleteMedIdPref(context: Context, appWidgetId: Int) {
    val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
    prefs.remove(PREF_PREFIX_KEY + appWidgetId + context.applicationContext.getString(R.string.med_id_key))
    prefs.apply()
}
