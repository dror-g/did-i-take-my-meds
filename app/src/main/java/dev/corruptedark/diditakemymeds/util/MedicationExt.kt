package dev.corruptedark.diditakemymeds.util

import android.content.Context
import android.text.format.DateFormat
import com.siravorona.utils.getCurrentLocale
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.Medication
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun doseString(
    yesterdayString: String,
    todayString: String,
    tomorrowString: String,
    doseTime: Long,
    dateFormat: String,
    timeFormat: String,
    locale: Locale
): String {
    val localizedFormatter = SimpleDateFormat(dateFormat, locale)

    val doseCalendar = Calendar.getInstance().apply { timeInMillis = doseTime }
    val todayCalendar = Calendar.getInstance()
    val yesterdayCalendar = (todayCalendar.clone() as Calendar).apply { add(Calendar.DATE, -1) }
    val tomorrowCalendar = (todayCalendar.clone() as Calendar).apply { add(Calendar.DATE, 1) }
    val dayString = if (doseCalendar.sameDay(todayCalendar)) {
        todayString
    } else if (doseCalendar.sameDay(yesterdayCalendar)) {
        yesterdayString
    } else if (doseCalendar.sameDay(tomorrowCalendar)) {
        tomorrowString
    } else {
        localizedFormatter.format(doseCalendar.timeInMillis) as String
    }
    val time = DateFormat.format(timeFormat, doseCalendar)
    val builder: StringBuilder = StringBuilder().append(time).append(" ").append(dayString)
    return builder.toString()
}

fun medicationDoseString(context: Context, timeInMillis: Long): String {
    val locale = context.getCurrentLocale()
    val yesterdayString = context.getString(R.string.yesterday)
    val todayString = context.getString(R.string.today)
    val tomorrowString = context.getString(R.string.tomorrow)
    val dateFormat = context.getString(R.string.date_format)
    val timeFormat = context.getTimeFormat()

    return doseString(
        yesterdayString,
        todayString,
        tomorrowString,
        timeInMillis,
        dateFormat,
        timeFormat,
        locale
    )
}
fun Medication.nextDoseString(context: Context): String {
    return medicationDoseString(context, calculateNextDose().timeInMillis)
}
fun Medication.closestDoseString(context: Context): String {
    return medicationDoseString(context, calculateClosestDose().timeInMillis)
}

fun Long.canBeRealId() = (this != 0L && this != -1L)

