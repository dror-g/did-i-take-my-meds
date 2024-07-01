package dev.corruptedark.diditakemymeds.util

import java.util.Calendar

fun Calendar.sameFieldValue(other: Calendar, field: Int) = this.get(field) == other.get(field)
fun Calendar.sameMonth(other: Calendar) = sameFieldValue(other, Calendar.MONTH)
fun Calendar.sameDate(other: Calendar) = sameFieldValue(other, Calendar.DATE)
fun Calendar.sameYear(other: Calendar) = sameFieldValue(other, Calendar.YEAR)
fun Calendar.sameDay(other: Calendar) = sameDate(other) && sameMonth(other) && sameYear(other)