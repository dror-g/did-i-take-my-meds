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
import android.os.Bundle
import androidx.activity.viewModels
import dev.corruptedark.diditakemymeds.databinding.SimpleSingleMedWidgetConfigureBinding
import androidx.lifecycle.lifecycleScope
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.BR
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


/**
 * The configuration screen for the [SimpleSingleMedWidgetDark] AppWidget.
 */
class SimpleSingleMedWidgetConfigureActivity :
    BaseBoundInteractableVmActivity<SimpleSingleMedWidgetConfigureBinding, ConfigureWidgetViewModel, ConfigureWidgetViewModel.Interactor>(
        SimpleSingleMedWidgetConfigureBinding::class, BR.vm) {
    val context = this
    val mainScope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override val vm : ConfigureWidgetViewModel by viewModels()

    override val modelInteractor = object : ConfigureWidgetViewModel.Interactor {
        override fun onMedicationTapped(medication: Medication) {
            this@SimpleSingleMedWidgetConfigureActivity.onMedicationTapped(medication)
        }
    }


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.title = getString(R.string.choose_a_medication)
        vm.initRecycler(binding.medListView)

        lifecycleScope.launch(Dispatchers.Default) {
            val medications = medicationDao(context).getAllRawFull()
            mainScope.launch {
                vm.setMedications(medications)
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

    private fun onMedicationTapped(medication: Medication) {
        val medId = medication.id
        saveMedIdPref(context, appWidgetId, medId)
        val layoutId = getLayoutPref(context, appWidgetId)

        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId, layoutId)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

}
