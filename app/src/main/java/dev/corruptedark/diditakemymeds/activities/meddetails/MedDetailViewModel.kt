package dev.corruptedark.diditakemymeds.activities.meddetails

import android.widget.CompoundButton
import androidx.databinding.Bindable
import androidx.recyclerview.widget.RecyclerView
import com.siravorona.utils.base.InteractableViewModel
import com.siravorona.utils.bindableproperty.bindableProperty
import com.siravorona.utils.listadapters.BindableAdapter
import com.siravorona.utils.lists.observableListOf
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.DoseUnit
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.MedicationType
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.ItemDoseListBinding

class MedDetailViewModel : InteractableViewModel<MedDetailViewModel.Interactor>() {
    interface Interactor {
        fun saveMedication(medication: Medication)
        fun scheduleNextMedicationAlarm(medication: Medication)
        fun cancelMedicationAlarm(medication: Medication)
        fun justTookItPressed(medication: Medication)
        fun promptDeleteDoseRecord(medication: Medication, doseRecord: DoseRecord)
        fun openDoseDetail(medication: Medication, doseRecord: DoseRecord)
    }

    private val doseRecordItems = observableListOf<DoseRecordItem>()

    @get:Bindable
    var medicationFull: MedicationFull by bindableProperty(
        MedicationFull.BLANK
    ) { oldValue, newValue ->
        doseRecordItems.clear()
        val records = newValue.medication.doseRecord
        doseRecordItems.addAll(records.map { DoseRecordItem(it) })
    }

    @get:Bindable("medicationFull")
    val medication: Medication
        get() {
            return medicationFull.medication
        }

    @get:Bindable("medicationFull")
    val medicationType: MedicationType
        get() {
            return medicationFull.medicationType
        }

    @get:Bindable("medicationFull")
    val doseUnit: DoseUnit
        get() {
            return medicationFull.doseUnit
        }

    @get:Bindable("doseUnit", "medication")
    val showDoseInfo: Boolean
        get() {
            return (medication.doseUnitId != Medication.DEFAULT_ID && medication.amountPerDose != Medication.UNDEFINED_AMOUNT)
        }

    @get:Bindable("medication")
    val showRemainingDoses: Boolean
        get() {
            return medication.remainingDoses != Medication.UNDEFINED_REMAINING
        }

    @get:Bindable("medication")
    val showTimeLabel: Boolean
        get() {
            return medication.isAsNeeded()
        }

    @get:Bindable("medication")
    val showClosestDose: Boolean
        get() {
            return medication.isAsNeeded()
        }

    @get:Bindable("medication")
    val showNotificationSwitch: Boolean
        get() {
            return medication.isAsNeeded()
        }

    @get:Bindable("medication")
    val showDescription: Boolean
        get() {
            return medication.description.isNotEmpty()
        }

    @get:Bindable("medication")
    val showSinceLastDose: Boolean
        get() {
            return medication.doseRecord.isNotEmpty()
        }


    fun onActiveCheckedChanged(switch: CompoundButton, checked: Boolean) {
        medication.active = checked
        interactor?.saveMedication(medication)
    }

    fun onNotifyCheckedChanged(switch: CompoundButton, checked: Boolean) {
        if (medication.notify == checked) return
        medication.notify = checked
        interactor?.saveMedication(medication)
        if (checked) {
            interactor?.scheduleNextMedicationAlarm(medication)
        } else {
            interactor?.cancelMedicationAlarm(medication)
        }
    }

    fun onJustTookItPressed() {
        interactor?.justTookItPressed(medication)
    }

    fun notifyMedicationPropertyChange() {
        notifyPropertyChanged(BR.medication)
    }

    fun setupDoseRecordsList(recyclerView: RecyclerView) {
        BindableAdapter(doseRecordItems, BR.item).map<DoseRecordItem, ItemDoseListBinding>(
            R.layout.item_dose_list
        ) {
            onLongClick {
                val record = it.binding.item?.doseRecord ?: return@onLongClick false
                interactor?.promptDeleteDoseRecord(medication, record)
                true
            }
            onClick {
                val record = it.binding.item?.doseRecord ?: return@onClick
                interactor?.openDoseDetail(medication, record)
            }
        }

            .into(recyclerView)
    }
}