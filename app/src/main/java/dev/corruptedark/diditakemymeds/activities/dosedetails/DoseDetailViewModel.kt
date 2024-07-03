package dev.corruptedark.diditakemymeds.activities.dosedetails

import android.net.Uri
import androidx.databinding.Bindable
import com.siravorona.utils.base.InteractableViewModel
import com.siravorona.utils.bindableproperty.bindableProperty
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import java.util.Calendar

class DoseDetailViewModel : InteractableViewModel<DoseDetailViewModel.Interactor>() {
    interface Interactor {
        suspend fun requestTime(initialHour: Int, initialMinute: Int): Pair<Int, Int>?
        suspend fun requestDate(initialSelectionMillis: Long): Triple<Int, Int, Int>?
        suspend fun confirmTakenTimeChanges(): Boolean
        fun saveTimeTaken(doseRecord: DoseRecord, newTimeTakenMills: Long)
    }

    @get:Bindable
    var doseRecord by bindableProperty(DoseRecord.BLANK) { oldValue, newValue ->
        workingCalendar.timeInMillis = newValue.doseTime
        notifyPropertyChanged(BR.workingCalendar)
    }

    @get:Bindable
    var imageUri: Uri? by bindableProperty(null)

    @get:Bindable("imageUri")
    val hasImage: Boolean
        get() {
            return imageUri != null
        }

    @get:Bindable
    var showEditTakenTime by bindableProperty(false)

    @get:Bindable
    val workingCalendar = Calendar.getInstance()

    @get:Bindable("doseRecord")
    val showClosestDose: Boolean
        get() {
            return doseRecord.closestDose != DoseRecord.NONE
        }

    fun onEditTakeTimeButtonTapped() {
        showEditTakenTime = !showEditTakenTime
    }

    fun onTimeTapped() {
        val interactor = interactor ?: return
        launchVmScope({
            val initialHour = workingCalendar.get(Calendar.HOUR_OF_DAY)
            val initialMinute = workingCalendar.get(Calendar.MINUTE)
            val (hour, minute) = interactor.requestTime(initialHour, initialMinute)
                    ?: return@launchVmScope
            workingCalendar.set(Calendar.HOUR_OF_DAY, hour)
            workingCalendar.set(Calendar.MINUTE, minute)
            notifyPropertyChanged(BR.workingCalendar)
        })
    }

    fun onDateTapped() {
        launchVmScope({
            val (year, month, day) = interactor?.requestDate(workingCalendar.timeInMillis)
                    ?: return@launchVmScope
            workingCalendar.set(Calendar.YEAR, year)
            workingCalendar.set(Calendar.MONTH, month)
            workingCalendar.set(Calendar.DAY_OF_MONTH, day)
            notifyPropertyChanged(BR.workingCalendar)
        })
    }

    fun onApplyTimeChangesTapped() {
        val interactor = this.interactor ?: return
        launchVmScope({
            if (hasTimeChanges()) {
                if (interactor.confirmTakenTimeChanges()) {
                    interactor.saveTimeTaken(this.doseRecord, workingCalendar.timeInMillis)
                    showEditTakenTime = false
                }
            }
        })
    }

    fun onCancelTimeChangesTapped() {
        workingCalendar.timeInMillis = doseRecord.doseTime
        notifyPropertyChanged(BR.workingCalendar)
        showEditTakenTime = false
    }


    private fun hasTimeChanges(): Boolean {
        return doseRecord.doseTime != workingCalendar.timeInMillis
    }
}