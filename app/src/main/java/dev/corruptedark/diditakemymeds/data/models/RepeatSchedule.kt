package dev.corruptedark.diditakemymeds.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Calendar

@Parcelize
data class RepeatSchedule(
    var hour: Int,
    var minute: Int,
    var startDay: Int,
    var startMonth: Int,
    var startYear: Int,
    var daysBetween: Int = 1,
    var weeksBetween: Int = 0,
    var monthsBetween: Int = 0,
    var yearsBetween: Int = 0
) : Parcelable {
    companion object {
        val BLANK = RepeatSchedule(-1, -1, -1, -1, -1)
        fun fromMedication(medication: Medication): RepeatSchedule {
            return RepeatSchedule(
                hour = medication.hour,
                minute = medication.minute,
                startDay = medication.startDay,
                startMonth = medication.startMonth,
                startYear = medication.startYear,
                daysBetween = medication.daysBetween,
                weeksBetween = medication.weeksBetween,
                monthsBetween = medication.monthsBetween,
                yearsBetween = medication.yearsBetween
            )
        }
    }

    fun isValid(isBirthControl: Boolean) : Boolean {
        val periodIsValid = daysBetween > 0 || weeksBetween > 0 || monthsBetween > 0 || yearsBetween > 0
        val startTimeIsValid = hour >= 0 && minute >= 0 && startDay >= 0 && startMonth >= 0 && startYear >= 0
        return (periodIsValid || isBirthControl) && startTimeIsValid
    }

    fun asBirthControl(birthControlType: BirthControlType): RepeatSchedule {
        if (birthControlType == BirthControlType.NO) return this
        return copy(daysBetween = birthControlType.days + 7, weeksBetween = 0, monthsBetween = 0, yearsBetween = 0)
    }

    fun fillCalendar(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.DAY_OF_MONTH, startDay)
        calendar.set(Calendar.MONTH, startMonth)
        calendar.set(Calendar.YEAR, startYear)
    }
}


enum class BirthControlType(val days: Int) {
    NO(0), THREE_WEEKS(21), NINE_WEEKS(63)
}