package dev.corruptedark.diditakemymeds.util.notifications

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.MainActivity
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.util.activityIntentFromIntent
import dev.corruptedark.diditakemymeds.util.broadcastIntentFromIntent
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.Executors


class ActionReceiver : BroadcastReceiver() {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    companion object {
        const val NOTIFY_ACTION = "NOTIFY"
        const val TOOK_MED_ACTION = "TOOK_MED"
        const val REMIND_ACTION = "REMIND"
        const val REMIND_DELAY = 15 //minutes
        const val CANCEL_DELAY = 2000L //milliseconds

        private const val NO_ICON = 0

        fun configureNotification(
            context: Context,
            medication: Medication,
            contentText: String? = null,
            noActions: Boolean = false
        ): NotificationCompat.Builder {
//            medicationDao(context).updateMedications(medication)

            val builder = NotificationsUtil.medicationNotificationBuilder(context, medication)

            val actionIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            if (contentText != null) builder.setContentText(contentText)

            val contentIntent = buildContentIntent(context, medication, actionIntent)
            builder.setContentIntent(contentIntent)

            if (!noActions) {
                val tookMedPendingIntent = buildActionTookMedIntent(context, medication)
                val remindPendingIntent = buildActionRemindIntent(context, medication)

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !medication.requirePhotoProof) {
                    builder.addAction(
                        NO_ICON, context.getString(R.string.took_it), tookMedPendingIntent
                    )
                }

                builder.addAction(
                    NO_ICON, context.getString(R.string.remind_in_15), remindPendingIntent
                )
            }

            return builder
        }

        //region pending intents
        private fun buildActionRemindIntent(
            context: Context,
            medication: Medication
        ): PendingIntent {
            val remindIntent = Intent(context, ActionReceiver::class.java).apply {
                action = REMIND_ACTION
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            return context.broadcastIntentFromIntent(medication.id.toInt(), remindIntent)
        }

        private fun buildActionTookMedIntent(
            context: Context,
            medication: Medication
        ): PendingIntent {
            val tookMedIntent = Intent(context, ActionReceiver::class.java).apply {
                action = TOOK_MED_ACTION
                putExtra(context.getString(R.string.med_id_key), medication.id)
            }
            return context.broadcastIntentFromIntent(medication.id.toInt(), tookMedIntent)
        }

        private fun buildContentIntent(
            context: Context,
            medication: Medication,
            actionIntent: Intent
        ): PendingIntent {
            return context.activityIntentFromIntent(medication.id.toInt(), actionIntent)
        }
    }
    // endregion


    private fun notify(
        context: Context, medication: Medication,
        contentText: String? = null,
        noActions: Boolean = false,
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val notification =
                configureNotification(
                    context, medication,
                    contentText = contentText,
                    noActions = noActions
                ).build()
            val nm = NotificationManagerCompat.from(context.applicationContext)
            nm.notify(
                medication.id.toInt(),
                notification
            )
        }
    }

    private suspend fun cancelNotification(context: Context, idToCancel: Int) {
        val nm = NotificationManagerCompat.from(context.applicationContext)
        delay(CANCEL_DELAY)
        nm.cancel(idToCancel)
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
        context: Context,
        intent: Intent
    ) {
        val medId = intent.getLongExtra(context.getString(R.string.med_id_key), -1L)
        if (medicationDao(context).medicationExists(medId)
            && medicationDao(context).get(medId).active
        ) {
            val medication: Medication =
                medicationDao(context).get(medId)

            if (medication.requirePhotoProof) {
                takePhotoProof(context, medication)
            } else {
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
                    medicationDao(context)
                        .updateMedications(medication)
                }

                notify(context, medication, context.getString(R.string.taken), true)
                cancelNotification(context, medication.id.toInt())
            }

        } else {
            with(NotificationManagerCompat.from(context.applicationContext)) {
                cancel(medId.toInt())
            }
        }
    }

    private fun takePhotoProof(
        context: Context,
        medication: Medication
    ) {
        val takeMedIntent = Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(context.getString(R.string.med_id_key), medication.id)
            putExtra(context.getString(R.string.take_med_key), true)
        }

        context.startActivity(takeMedIntent)
    }

    private fun onRemindAction(context: Context, intent: Intent) {
        val medId = intent.getLongExtra(context.getString(R.string.med_id_key), -1L)

        with(NotificationManagerCompat.from(context.applicationContext)) {
            cancel(medId.toInt())
        }

        if (medicationDao(context).medicationExists(medId)) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, REMIND_DELAY)
            val medication: Medication =
                medicationDao(context).get(medId)
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
                    notify(context, medication)
                }
            }
        }
        medicationDao(context)
            .updateMedications(*medications.toTypedArray())
    }

    private fun onNotifyAction(context: Context, intent: Intent) {
        val medication =
            medicationDao(context)
                .get(intent.getLongExtra(context.getString(R.string.med_id_key), -1))

        medication.updateStartsToFuture()
        AlarmIntentManager.scheduleMedicationAlarm(context, medication)

        if (medication.active && !medication.closestDoseAlreadyTaken()) {
            notify(context, medication)
        }
    }
}
