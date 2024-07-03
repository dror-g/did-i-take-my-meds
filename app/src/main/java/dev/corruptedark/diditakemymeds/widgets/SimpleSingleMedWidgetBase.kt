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
import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.lifecycle.Observer
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.models.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in [SimpleSingleMedWidgetConfigureActivity]
 */
abstract class SimpleSingleMedWidgetBase(protected val layoutId: Int) : AppWidgetProvider() {

    companion object {
        private var appWidgetIds: IntArray? = null
        private val MAXIMUM_DELAY = 60000L // 1 minute in milliseconds
        private val MINIMUM_DELAY = 1000L // 1 second in milliseconds
        val mainScope = MainScope()
        var databaseObserver: Observer<MutableList<Medication>>? = null
        var refresher: Job? = null
    }

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
    ) {
        Companion.appWidgetIds = appWidgetIds

        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            saveLayoutPref(context, appWidgetId, layoutId)
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            deleteMedIdPref(context, appWidgetId)
            deleteLayoutPref(context, appWidgetId)
        }
    }

    private fun startRefresherLoop(context: Context): Job {
        return GlobalScope.launch(Dispatchers.IO) {

            while (medicationDao(context).getAllRaw().isNotEmpty()) {
                val medication = medicationDao(context).getAllRaw()
                        .sortedWith(Medication::compareByClosestDoseTransition).first()

                val transitionDelay =
                        medication.closestDoseTransitionTime() - System.currentTimeMillis()

                val delayDuration =
                        when {
                            transitionDelay < MINIMUM_DELAY -> {
                                MINIMUM_DELAY
                            }

                            transitionDelay in MINIMUM_DELAY until MAXIMUM_DELAY -> {
                                transitionDelay
                            }

                            else -> {
                                MAXIMUM_DELAY
                            }
                        }

                delay(delayDuration)
                appWidgetIds?.apply {
                    onUpdate(context, AppWidgetManager.getInstance(context), this)
                }
            }
        }
    }

    private suspend fun stopRefresherLoop(refresher: Job?) {
        runCatching {
            refresher?.cancelAndJoin()
        }.onFailure { throwable ->
            throwable.printStackTrace()
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        databaseObserver = Observer {
            appWidgetIds?.apply {
                onUpdate(context, AppWidgetManager.getInstance(context), this)
            }
        }

        refresher = startRefresherLoop(context)

        mainScope.launch {
            medicationDao(context).getAllLiveData().observeForever(databaseObserver!!)
        }
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        databaseObserver?.apply {
            medicationDao(context).getAllLiveData().removeObserver(this)
        }

        runBlocking {
            stopRefresherLoop(refresher)
        }
    }
}
