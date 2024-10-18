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

package dev.corruptedark.diditakemymeds.data.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.corruptedark.diditakemymeds.data.db.MedicationDB
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign

@Entity(tableName = MedicationDB.MED_TABLE)
data class Medication(
    @PrimaryKey(autoGenerate = true)
    var id: Long = DEFAULT_ID,
    var name: String,
    var hour: Int = RepeatSchedule.BLANK.hour,
    var minute: Int = RepeatSchedule.BLANK.minute,
    var description: String,
    var startDay: Int = RepeatSchedule.BLANK.startDay,
    var startMonth: Int = RepeatSchedule.BLANK.startMonth,
    var startYear: Int = RepeatSchedule.BLANK.startYear,
    var daysBetween: Int = RepeatSchedule.BLANK.daysBetween,
    var weeksBetween: Int = RepeatSchedule.BLANK.weeksBetween,
    var monthsBetween: Int = RepeatSchedule.BLANK.monthsBetween,
    var yearsBetween: Int = RepeatSchedule.BLANK.yearsBetween,
    var notify: Boolean = true,
    var requirePhotoProof: Boolean = true,
    var active: Boolean = true,
    var typeId: Long = DEFAULT_ID,
    var rxNumber: String = UNDEFINED,
    var pharmacy: String = UNDEFINED,
    var doseUnitId: Long = DEFAULT_ID,
    var amountPerDose: Double = UNDEFINED_AMOUNT,
    var remainingDoses: Int = UNDEFINED_REMAINING,
    var takeWithFood: Boolean = false,
    @ColumnInfo(name = "dose_record") var doseRecord: ArrayList<DoseRecord> = ArrayList(),
    var moreDosesPerDay: ArrayList<RepeatSchedule> = ArrayList()
) {
    companion object {

        const val FALLBACK_TRANSITION_TIME = Long.MAX_VALUE
        const val INVALID_MED_ID = -1L
        const val UNDEFINED = ""
        const val DEFAULT_ID = 0L
        const val UNDEFINED_AMOUNT = -99.0
        const val UNDEFINED_REMAINING = -99

        val BLANK = Medication(name = "", description = "")

        fun doseString(
            yesterdayString: String,
            todayString: String,
            tomorrowString: String,
            doseTime: Long,
            dateFormat: String,
            timeFormat: String,
            locale: Locale
        ): String {
            return dev.corruptedark.diditakemymeds.util.doseString(
                yesterdayString,
                todayString,
                tomorrowString,
                doseTime,
                dateFormat,
                timeFormat,
                locale
            )
        }

        fun compareByName(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)

            return if (byActive != 0) {
                byActive
            } else {
                a.name.compareTo(b.name)
            }
        }

        fun compareByTime(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)
            val aDose = a.calculateClosestDose()
            val bDose = b.calculateClosestDose()

            return when {
                byActive != 0 -> {
                    byActive
                }

                aDose.timeInMillis == bDose.timeInMillis -> {
                    compareByName(a, b)
                }

                else -> {
                    (aDose.timeInMillis - bDose.timeInMillis).sign
                }
            }
        }

        fun compareByClosestDoseTransition(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)
            val aTransition = a.closestDoseTransitionTime()
            val bTransition = b.closestDoseTransitionTime()


            return if (byActive != 0) {
                byActive
            } else {
                (aTransition - bTransition).sign
            }
        }

        fun compareByType(a: Medication, b: Medication): Int {
            val byActive = compareByActive(a, b)
            val byName = compareByName(a, b)
            val byType = (a.typeId - b.typeId).sign

            return when {
                byActive != 0 -> {
                    byActive
                }

                byType == 0 -> {
                    byName
                }

                else -> {
                    byType
                }
            }
        }

        private fun compareByActive(a: Medication, b: Medication): Int {
            return when {
                a.active && !b.active -> {
                    -1
                }

                b.active && !a.active -> {
                    1
                }

                else -> {
                    0
                }
            }
        }
    }

    fun shouldNotify(): Boolean {
        return active && notify
    }

    /**
     * Updates the start times of schedules in this medication to future times
     *
     * Warning: This only updates in the current instance of the medication, database updates must happen elsewhere
     * It is recommended to call this before calculating next and closest doses
     */
    fun updateStartsToFuture() {
        if (!isAsNeeded()) {
            val localCalendar = Calendar.getInstance()
            val currentTime = localCalendar.timeInMillis

            localCalendar.set(Calendar.HOUR_OF_DAY, hour)
            localCalendar.set(Calendar.MINUTE, minute)
            localCalendar.set(Calendar.SECOND, 0)
            localCalendar.set(Calendar.MILLISECOND, 0)
            localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
            localCalendar.set(Calendar.MONTH, startMonth)
            localCalendar.set(Calendar.YEAR, startYear)

            if (currentTime >= localCalendar.timeInMillis) {
                while (currentTime >= localCalendar.timeInMillis) {
                    localCalendar.add(Calendar.DATE, daysBetween)
                    localCalendar.add(Calendar.WEEK_OF_YEAR, weeksBetween)
                    localCalendar.add(Calendar.MONTH, monthsBetween)
                    localCalendar.add(Calendar.YEAR, yearsBetween)
                }

                startDay = localCalendar.get(Calendar.DAY_OF_MONTH)
                startMonth = localCalendar.get(Calendar.MONTH)
                startYear = localCalendar.get(Calendar.YEAR)
            }

            moreDosesPerDay.forEach { schedule ->
                localCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
                localCalendar.set(Calendar.MINUTE, schedule.minute)
                localCalendar.set(Calendar.SECOND, 0)
                localCalendar.set(Calendar.MILLISECOND, 0)
                localCalendar.set(Calendar.DAY_OF_MONTH, schedule.startDay)
                localCalendar.set(Calendar.MONTH, schedule.startMonth)
                localCalendar.set(Calendar.YEAR, schedule.startYear)

                if (currentTime >= localCalendar.timeInMillis) {
                    while (currentTime >= localCalendar.timeInMillis) {
                        localCalendar.add(Calendar.DATE, schedule.daysBetween)
                        localCalendar.add(Calendar.WEEK_OF_YEAR, schedule.weeksBetween)
                        localCalendar.add(Calendar.MONTH, schedule.monthsBetween)
                        localCalendar.add(Calendar.YEAR, schedule.yearsBetween)
                    }

                    schedule.startDay = localCalendar.get(Calendar.DAY_OF_MONTH)
                    schedule.startMonth = localCalendar.get(Calendar.MONTH)
                    schedule.startYear = localCalendar.get(Calendar.YEAR)
                }
            }
        }
    }

    fun calculateNextDose(): ScheduleSortTriple {
        val scheduleTripleList = ArrayList<ScheduleSortTriple>()

        val localCalendar = Calendar.getInstance()
        val currentTime = localCalendar.timeInMillis
        var scheduleTriple: ScheduleSortTriple
        var nextDose: ScheduleSortTriple

        localCalendar.set(Calendar.HOUR_OF_DAY, hour)
        localCalendar.set(Calendar.MINUTE, minute)
        localCalendar.set(Calendar.SECOND, 0)
        localCalendar.set(Calendar.MILLISECOND, 0)
        localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
        localCalendar.set(Calendar.MONTH, startMonth)
        localCalendar.set(Calendar.YEAR, startYear)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
            -1
        )

        nextDose = scheduleTriple

        scheduleTripleList.add(scheduleTriple)

        moreDosesPerDay.forEachIndexed { index, schedule ->
            localCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
            localCalendar.set(Calendar.MINUTE, schedule.minute)
            localCalendar.set(Calendar.SECOND, 0)
            localCalendar.set(Calendar.MILLISECOND, 0)
            localCalendar.set(Calendar.DAY_OF_MONTH, schedule.startDay)
            localCalendar.set(Calendar.MONTH, schedule.startMonth)
            localCalendar.set(Calendar.YEAR, schedule.startYear)

            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)
        }

        scheduleTripleList.sort()

        for (triple in scheduleTripleList) {
            if (triple.timeInMillis > currentTime) {
                nextDose = triple
                break
            }
        }

        return nextDose
    }

    fun calculateClosestDose(): ScheduleSortTriple {
        val scheduleTripleList = ArrayList<ScheduleSortTriple>()

        val localCalendar = Calendar.getInstance()
        val currentTime = localCalendar.timeInMillis
        var scheduleTriple: ScheduleSortTriple
        var closestDose: ScheduleSortTriple

        localCalendar.set(Calendar.HOUR_OF_DAY, hour)
        localCalendar.set(Calendar.MINUTE, minute)
        localCalendar.set(Calendar.SECOND, 0)
        localCalendar.set(Calendar.MILLISECOND, 0)
        localCalendar.set(Calendar.DAY_OF_MONTH, startDay)
        localCalendar.set(Calendar.MONTH, startMonth)
        localCalendar.set(Calendar.YEAR, startYear)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
            -1
        )

        closestDose = scheduleTriple

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, -daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, -weeksBetween)
        localCalendar.add(Calendar.MONTH, -monthsBetween)
        localCalendar.add(Calendar.YEAR, -yearsBetween)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
            -1
        )

        scheduleTripleList.add(scheduleTriple)

        localCalendar.add(Calendar.DATE, 2 * daysBetween)
        localCalendar.add(Calendar.WEEK_OF_YEAR, 2 * weeksBetween)
        localCalendar.add(Calendar.MONTH, 2 * monthsBetween)
        localCalendar.add(Calendar.YEAR, 2 * yearsBetween)
        scheduleTriple = ScheduleSortTriple(
            localCalendar.timeInMillis,
            RepeatSchedule(
                hour,
                minute,
                startDay,
                startMonth,
                startYear,
                daysBetween,
                weeksBetween,
                monthsBetween,
                yearsBetween
            ),
            -1
        )

        scheduleTripleList.add(scheduleTriple)

        moreDosesPerDay.forEachIndexed { index, schedule ->
            localCalendar.set(Calendar.HOUR_OF_DAY, schedule.hour)
            localCalendar.set(Calendar.MINUTE, schedule.minute)
            localCalendar.set(Calendar.SECOND, 0)
            localCalendar.set(Calendar.MILLISECOND, 0)
            localCalendar.set(Calendar.DAY_OF_MONTH, schedule.startDay)
            localCalendar.set(Calendar.MONTH, schedule.startMonth)
            localCalendar.set(Calendar.YEAR, schedule.startYear)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)

            localCalendar.add(Calendar.DATE, -schedule.daysBetween)
            localCalendar.add(Calendar.WEEK_OF_YEAR, -schedule.weeksBetween)
            localCalendar.add(Calendar.MONTH, -schedule.monthsBetween)
            localCalendar.add(Calendar.YEAR, -schedule.yearsBetween)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)

            localCalendar.add(Calendar.DATE, 2 * schedule.daysBetween)
            localCalendar.add(Calendar.WEEK_OF_YEAR, 2 * schedule.weeksBetween)
            localCalendar.add(Calendar.MONTH, 2 * schedule.monthsBetween)
            localCalendar.add(Calendar.YEAR, 2 * schedule.yearsBetween)
            scheduleTriple = ScheduleSortTriple(localCalendar.timeInMillis, schedule, index)

            scheduleTripleList.add(scheduleTriple)
        }

        scheduleTripleList.sortWith { schedule1, schedule2 ->
            (abs(schedule1.timeInMillis - currentTime) - abs(schedule2.timeInMillis - currentTime)).sign
        }

        closestDose = scheduleTripleList.first()

        return closestDose
    }

    fun closestDoseAlreadyTaken(): Boolean {
        val lastDose: Long = try {
            doseRecord.first().closestDose
        } catch (except: NoSuchElementException) {
            INVALID_MED_ID
        }
        val closestDose = calculateClosestDose().timeInMillis

        return lastDose == closestDose
    }

    /**
     * Finds the time at which the closest dose will change
     */
    fun closestDoseTransitionTime(): Long {
        updateStartsToFuture()
        return if (!isAsNeeded()) {
            ((calculateClosestDose().timeInMillis.toBigInteger() + calculateNextDose().timeInMillis.toBigInteger()) / 2L.toBigInteger()).toLong() + 1L
        } else {
            FALLBACK_TRANSITION_TIME
        }
    }

    fun timeSinceLastTakenDose(): Long {
        return if (doseRecord.isNotEmpty()) {
            System.currentTimeMillis() - doseRecord.first().doseTime
        } else {
            System.currentTimeMillis()
        }
    }

    fun hasDoseRemaining(): Boolean {
        return remainingDoses > 0 || remainingDoses == UNDEFINED_REMAINING
    }

    fun addNewTakenDose(takenDose: DoseRecord) {
        doseRecord.add(takenDose)
        if (remainingDoses > 0) {
            remainingDoses--
        }
        doseRecord.sort()
    }

    fun removeTakenDose(doseIndex: Int, realDose: Boolean) {
        doseRecord.removeAt(doseIndex)
        if (!realDose && remainingDoses >= 0) {
            remainingDoses++
        }
    }

    fun removeTakenDose(takenDose: DoseRecord, realDose: Boolean) {
        if (doseRecord.remove(takenDose) && !realDose && remainingDoses >= 0) {
            remainingDoses++
        }
    }

    fun isAsNeeded(): Boolean {
        return hour < 0 || minute < 0 || startDay < 0 || startMonth < 0 || startYear < 0
    }
}