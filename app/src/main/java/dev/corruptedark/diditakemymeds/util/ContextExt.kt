package dev.corruptedark.diditakemymeds.util

import android.content.Context
import android.text.format.DateFormat
import dev.corruptedark.diditakemymeds.R
import java.util.Calendar

fun Context.formatTime(millis: Long): String {
    val timeFormat = getTimeFormat()
    val text = DateFormat.format(timeFormat, millis)
    return text.toString()
}

fun Context.formatDate(millis: Long): String {
    val timeFormat = getTimeFormat()
    val text = DateFormat.format(timeFormat, millis)
    return text.toString()
}

fun Context.formatTime(calendar: Calendar): String {
    val timeFormat = getTimeFormat()
    val text = DateFormat.format(timeFormat, calendar)
    return text.toString()
}

fun Context.formatDate(calendar: Calendar): String {
    val timeFormat = getDateFormat()
    val text = DateFormat.format(timeFormat, calendar)
    return text.toString()
}

fun Context.getTimeFormat(): String {
    val systemIs24Hour = DateFormat.is24HourFormat(this)
    val timeFormat =
        if (systemIs24Hour) this.getString(R.string.time_24) else this.getString(R.string.time_12)
    return timeFormat
}

fun Context.getDateFormat(): String {
    return getString(R.string.date_format)
}