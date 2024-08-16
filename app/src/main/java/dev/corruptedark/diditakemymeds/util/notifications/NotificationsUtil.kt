package dev.corruptedark.diditakemymeds.util.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.siravorona.utils.permissions.isPermissionGranted
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.main.MainActivity
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.util.activityIntentFromIntent
import dev.corruptedark.diditakemymeds.util.broadcastIntentFromIntent
import dev.corruptedark.diditakemymeds.util.formatTime
import kotlinx.coroutines.delay
import java.util.Calendar

object NotificationsUtil {

    private const val NO_ICON = 0
    private val CHANNEL_MAIN = "did_i_take_my_meds"
    private val CHANNEL_TAKEN = "med_taken"

    fun createNotificationChannels(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(buildMainChannel(context))
            notificationManager.createNotificationChannel(buildMedTakenChannel(context))

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildMainChannel(context: Context): NotificationChannel {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_MAIN, name, importance).apply {
            description = descriptionText
        }
        return channel
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildMedTakenChannel(context: Context): NotificationChannel {
        val name = context.getString(R.string.channel_med_taken_name)
        val descriptionText = context.getString(R.string.channel_med_taken_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_TAKEN, name, importance).apply {
            description = descriptionText
        }
        return channel
    }

    fun notifyOnMainChannel(
        context: Context,
        medication: Medication,
        contentText: String? = null,
        noActions: Boolean = false,
    ) = notify(context, medication, CHANNEL_MAIN, contentText, noActions)

    fun notifyOnMedTakenChannel(
        context: Context,
        medication: Medication,
        contentText: String? = null,
        noActions: Boolean = false,
    ) = notify(context, medication, CHANNEL_TAKEN, contentText, noActions)

    @SuppressLint("MissingPermission")
    fun notify(
        context: Context,
        medication: Medication,
        channel: String = CHANNEL_MAIN,
        contentText: String? = null,
        noActions: Boolean = false,
    ) {
        if (context.isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            val notification = configureMedicationNotification(
                context,
                medication,
                contentText = contentText,
                noActions = noActions,
                channel = channel
            ).build()
            val nm = NotificationManagerCompat.from(context)
            nm.notify(medication.id.toInt(), notification)
        }
    }

    fun cancelNotification(context: Context, idToCancel: Int) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(idToCancel)
    }

    suspend fun cancelNotificationWithDelay(context: Context, idToCancel: Int, delayMillis: Long) {
        val nm = NotificationManagerCompat.from(context)
        delay(delayMillis)
        nm.cancel(idToCancel)
    }

    private fun medicationNotificationBuilder(
        context: Context,
        medication: Medication,
        channel: String
    ): NotificationCompat.Builder {
        val calendar = Calendar.getInstance()
        val closestDose = medication.calculateClosestDose()
        val hour = closestDose.schedule.hour
        val minute = closestDose.schedule.minute
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        val formattedTime = context.formatTime(calendar)

        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_small_notification).setColor(
                ResourcesCompat.getColor(
                    context.resources,
                    R.color.notification_icon_color,
                    context.theme
                )
            ).setContentTitle(medication.name).setSubText(formattedTime)
            .setContentText(context.getString(R.string.time_for_your_dose))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(false)
    }


    private fun configureMedicationNotification(
        context: Context,
        medication: Medication,
        contentText: String? = null,
        noActions: Boolean = false,
        channel: String
    ): NotificationCompat.Builder {

        val builder = medicationNotificationBuilder(context, medication, channel)

        val actionIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(context.getString(R.string.med_id_key), medication.id)
        }
        if (contentText != null) builder.setContentText(contentText)

        val contentIntent = buildContentIntent(context, medication, actionIntent)
        builder.setContentIntent(contentIntent)

        if (!noActions) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !medication.requirePhotoProof) {
                val tookMedPendingIntent = buildActionTookMedIntent(context, medication)
                builder.addAction(
                    NO_ICON,
                    context.getString(R.string.took_it),
                    tookMedPendingIntent
                )
            }

            val remindPendingIntent = buildActionRemindIntent(context, medication)
            builder.addAction(
                NO_ICON,
                context.getString(R.string.remind_in_15),
                remindPendingIntent
            )
        }

        return builder
    }

    //region pending intents
    fun buildNotificationAlarmIntent(context: Context, medication: Medication): PendingIntent {
        return Intent(context, ActionReceiver::class.java).let { innerIntent ->
            innerIntent.action = ActionReceiver.NOTIFY_ACTION
            innerIntent.putExtra(context.getString(R.string.med_id_key), medication.id)
            context.broadcastIntentFromIntent(medication.id.toInt(), innerIntent)
        }
    }

    private fun buildActionRemindIntent(context: Context, medication: Medication): PendingIntent {
        val remindIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.REMIND_ACTION
            putExtra(context.getString(R.string.med_id_key), medication.id)
        }
        return context.broadcastIntentFromIntent(medication.id.toInt(), remindIntent)
    }

    private fun buildActionTookMedIntent(context: Context, medication: Medication): PendingIntent {
        val tookMedIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.TOOK_MED_ACTION
            putExtra(context.getString(R.string.med_id_key), medication.id)
        }
        return context.broadcastIntentFromIntent(medication.id.toInt(), tookMedIntent)
    }

    private fun buildContentIntent(
        context: Context, medication: Medication, actionIntent: Intent
    ): PendingIntent {
        return context.activityIntentFromIntent(medication.id.toInt(), actionIntent)
    }
}
// endregion

