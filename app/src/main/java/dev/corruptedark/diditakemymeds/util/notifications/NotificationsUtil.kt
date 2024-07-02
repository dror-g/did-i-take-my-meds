package dev.corruptedark.diditakemymeds.util.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.util.formatTime
import java.util.Calendar

object NotificationsUtil {
    fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(name, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

        }
    }

    fun medicationNotificationBuilder(
        context: Context,
        medication: Medication,
    ): NotificationCompat.Builder {
        val calendar = Calendar.getInstance()
        val closestDose = medication.calculateClosestDose()
        val hour = closestDose.schedule.hour
        val minute = closestDose.schedule.minute
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        val formattedTime = context.formatTime(calendar)

        return NotificationCompat.Builder(context, context.getString(R.string.channel_name))
            .setSmallIcon(R.drawable.ic_small_notification)
            .setColor(
                ResourcesCompat.getColor(
                    context.resources,
                    R.color.notification_icon_color,
                    context.theme
                )
            )
            .setContentTitle(medication.name)
            .setSubText(formattedTime)
            .setContentText(context.getString(R.string.time_for_your_dose))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
    }
}
