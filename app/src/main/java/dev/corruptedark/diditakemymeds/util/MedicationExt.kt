package dev.corruptedark.diditakemymeds.util

import android.content.Context
import android.text.format.DateFormat
import com.siravorona.utils.getCurrentLocale
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.Medication

fun medicationDoseString(context: Context, timeInMillis: Long): String {
    val locale = context.getCurrentLocale()
    val yesterdayString = context.getString(R.string.yesterday)
    val todayString = context.getString(R.string.today)
    val tomorrowString = context.getString(R.string.tomorrow)
    val dateFormat = context.getString(R.string.date_format)
    val systemIs24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = if (systemIs24Hour) context.getString(R.string.time_24) else context.getString(R.string.time_12)

    return Medication.doseString(
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