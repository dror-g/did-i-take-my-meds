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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.main.MainActivity
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.util.broadcastIntentFromIntent

object AlarmIntentManager {

    fun scheduleMedicationAlarm(
        context: Context, medication: Medication,
        customTimeMillis: Long? = null,
    ): PendingIntent {
        val alarmIntent = NotificationsUtil.buildNotificationAlarmIntent(context, medication)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmIntent)
        setExactAlarm(
            alarmManager, alarmIntent,
            customTimeMillis ?: medication.calculateNextDose().timeInMillis
        )

        val receiver = ComponentName(context, ActionReceiver::class.java)

        context.packageManager.setComponentEnabledSetting(
            receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
        return alarmIntent
    }

    fun cancelAlarm(context: Context, medication: Medication) {
        val alarmIntent = NotificationsUtil.buildNotificationAlarmIntent(context, medication)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmIntent)
    }

    private fun setExactAlarm(alarmManager: AlarmManager, alarmIntent: PendingIntent, timeInMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (canScheduleExactAlarms(alarmManager)) {
                    //  alarms set with setAlarmClock appear to persist after process is closed
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(timeInMillis, alarmIntent), alarmIntent)
                }
            } catch (e: SecurityException) {
                //
            }
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP, timeInMillis, alarmIntent
            )
        }
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager) : Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}