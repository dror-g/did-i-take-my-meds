package dev.corruptedark.diditakemymeds.util.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.main.MainActivity
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.Medication
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.Executors


class ActionReceiver : BroadcastReceiver() {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    companion object {
        const val NOTIFY_ACTION = "NOTIFY"
        const val TOOK_MED_ACTION = "TOOK_MED"
        const val REMIND_ACTION = "REMIND"

        const val REMIND_DELAY = 15 //minutes
        const val CANCEL_DELAY = 2000L //milliseconds

    }


    override fun onReceive(context: Context, intent: Intent) {
        NotificationsUtil.createNotificationChannel(context)

        GlobalScope.launch(dispatcher) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    onStartEverythingAction(context)
                }

                NOTIFY_ACTION -> {
                    //Handle alarm
                    onNotifyAction(context, intent)
                }

                TOOK_MED_ACTION -> {
                    onTookMedAction(context, intent)
                }

                REMIND_ACTION -> {
                    onRemindAction(context, intent)
                }
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
                DoseRecord(System.currentTimeMillis(),
                        medication.calculateClosestDose().timeInMillis)
            }
            medication.addNewTakenDose(takenDose)
            medicationDao(context).updateOrCreate(medication)
        }

        NotificationsUtil.notify(context, medication, context.getString(R.string.taken), true)
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

    private fun onStartEverythingAction(context: Context) {
        val medications = medicationDao(context).getAllRaw()
        medications.forEach { medication ->
            medication.updateStartsToFuture()
            if (medication.notify) {
                //Create alarm
                AlarmIntentManager.scheduleMedicationAlarm(context, medication)
                if (System.currentTimeMillis() > medication.calculateClosestDose().timeInMillis && !medication.closestDoseAlreadyTaken()) {
                    NotificationsUtil.notify(context, medication)
                }
            }
        }
        medicationDao(context).update(medications)
    }

    private fun onNotifyAction(context: Context, intent: Intent) {
        val medication = medicationDao(context).getById(intent.getLongExtra(context.getString(R.string.med_id_key),
                -1))

        medication.updateStartsToFuture()
        AlarmIntentManager.scheduleMedicationAlarm(context, medication)

        if (medication.active && !medication.closestDoseAlreadyTaken()) {
            NotificationsUtil.notify(context, medication)
        }
    }
}

