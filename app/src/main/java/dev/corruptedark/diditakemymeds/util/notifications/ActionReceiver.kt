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

package dev.corruptedark.diditakemymeds.util.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.main.MainActivity
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.Medication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar


class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFY_ACTION = "NOTIFY"
        const val TOOK_MED_ACTION = "TOOK_MED"
        const val REMIND_ACTION = "REMIND"

        const val REMIND_DELAY = 15 //minutes
        const val CANCEL_DELAY = 2000L //milliseconds

        fun onStartEverything(context: Context) {
            val medications = medicationDao(context).getAllRaw()
            medications.forEach { medication ->
                medication.updateStartsToFuture()
                if (medication.shouldNotify()) {
                    AlarmIntentManager.scheduleMedicationAlarm(context, medication)
                    if (System.currentTimeMillis() > medication.calculateClosestDose().timeInMillis
                        && !medication.closestDoseAlreadyTaken()
                    ) {
                        NotificationsUtil.notifyOnMainChannel(context, medication)
                    }
                }
            }
            medicationDao(context).update(medications)
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        NotificationsUtil.createNotificationChannels(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    NOTIFY_ACTION -> {
                        onNotifyAction(context, intent)
                    }

                    TOOK_MED_ACTION -> {
                        onTookMedAction(context, intent)
                    }

                    REMIND_ACTION -> {
                        onRemindAction(context, intent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun onTookMedAction(
        context: Context, intent: Intent
    ) {
        val medId = intent.getLongExtra(context.getString(R.string.med_id_key), -1L)
        if (!medicationDao(context).medicationExists(medId)) {
            NotificationsUtil.cancelNotification(context.applicationContext, medId.toInt())
            return
        }
        val medication = medicationDao(context).getById(medId)

        if (!medication.active) {
            NotificationsUtil.cancelNotification(context.applicationContext, medId.toInt())
            return
        }

        if (medication.requirePhotoProof) {
            takePhotoProof(context, medication)
            return
        }

        if (!medication.closestDoseAlreadyTaken() && medication.hasDoseRemaining()) {
            val takenDose = if (medication.isAsNeeded()) {
                DoseRecord(System.currentTimeMillis())
            } else {
                DoseRecord(
                    System.currentTimeMillis(),
                    medication.calculateClosestDose().timeInMillis
                )
            }
            medication.addNewTakenDose(takenDose)
            medicationDao(context).updateOrCreate(medication)
        }

        NotificationsUtil.notifyOnMedTakenChannel(
            context,
            medication,
            context.getString(R.string.taken),
            noActions = true
        )
        NotificationsUtil.cancelNotificationWithDelay(context, medication.id.toInt(), CANCEL_DELAY)
    }

    private fun takePhotoProof(
        context: Context, medication: Medication
    ) {
        val takeMedIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(context.getString(R.string.med_id_key), medication.id)
            putExtra(context.getString(R.string.take_med_key), true)
        }

        context.startActivity(takeMedIntent)
    }

    private fun onRemindAction(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(context.getString(R.string.med_id_key), -1L)

        NotificationsUtil.cancelNotification(context, medId.toInt())

        if (medicationDao(context).medicationExists(medId)) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, REMIND_DELAY)
            val medication: Medication = medicationDao(context).getById(medId)
            AlarmIntentManager.scheduleMedicationAlarm(context, medication, calendar.timeInMillis)

        }
    }

    private fun onNotifyAction(context: Context, intent: Intent) {
        val medication = medicationDao(context).getById(
            intent.getLongExtra(
                context.getString(R.string.med_id_key),
                -1
            )
        )

        medication.updateStartsToFuture()
        AlarmIntentManager.scheduleMedicationAlarm(context, medication)

        if (medication.active && !medication.closestDoseAlreadyTaken()) {
            NotificationsUtil.notifyOnMainChannel(context, medication)
        }
    }
}
