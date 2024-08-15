package dev.corruptedark.diditakemymeds.activities.add_edit_med

import android.widget.AutoCompleteTextView
import androidx.databinding.Bindable
import androidx.recyclerview.widget.RecyclerView
import com.siravorona.utils.base.InteractableViewModel
import com.siravorona.utils.bindableproperty.bindableProperty
import com.siravorona.utils.listadapters.BindableAdapter
import com.siravorona.utils.lists.observableListOf
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.BirthControlType
import dev.corruptedark.diditakemymeds.data.models.DoseUnit
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.MedicationType
import dev.corruptedark.diditakemymeds.data.models.RepeatSchedule
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.ExtraDoseTemplate2Binding
import dev.corruptedark.diditakemymeds.listadapters.DoseUnitListAdapter2
import dev.corruptedark.diditakemymeds.listadapters.MedTypeListAdapter2
import dev.corruptedark.diditakemymeds.util.addDefaultDivider
import java.util.Calendar

class MedViewModel : InteractableViewModel<MedViewModel.Interactor>() {
    interface Interactor {
        suspend fun openSchedulePicker(): Pair<RepeatSchedule, BirthControlType>?
        suspend fun rescheduleDose(
                index: Int,
                schedule: RepeatSchedule
        ): Pair<RepeatSchedule, BirthControlType>?
    }

    @get:Bindable
    var name by bindableProperty("")

    @get:Bindable
    var description by bindableProperty("")

    @get:Bindable
    var rxNumber by bindableProperty("")

    @get:Bindable
    var pharmacy by bindableProperty("")

    @get:Bindable
    var medTypeString by bindableProperty("")

    @get:Bindable
    var doseUnitString by bindableProperty("")

    @get:Bindable
    var amountPerDose by bindableProperty(Medication.UNDEFINED_AMOUNT) { _, newValue ->
        amountPerDoseString = if (newValue == Medication.UNDEFINED_AMOUNT) "" else newValue.toString()
    }

    @get:Bindable
    var amountPerDoseString: String by bindableProperty("") { _, newValue ->
        val number = newValue.toDoubleOrNull() ?: Medication.UNDEFINED_AMOUNT
        amountPerDose = number
    }

    @get:Bindable
    var remainingDoses by bindableProperty(Medication.UNDEFINED_REMAINING) { _, newValue ->
        remainingDosesString = if (newValue == Medication.UNDEFINED_REMAINING) "" else newValue.toString()
    }

    @get:Bindable
    var remainingDosesString: String by bindableProperty("") { _, newValue ->
        val number = newValue.toIntOrNull() ?: Medication.UNDEFINED_REMAINING
        remainingDoses = number
    }


    @get:Bindable
    var schedule by bindableProperty(RepeatSchedule.BLANK) { _, newValue ->
        canAddExtraDose = (newValue != RepeatSchedule.BLANK)
    }

    @get:Bindable
    var asNeeded by bindableProperty(true)

    @get:Bindable
    var notify by bindableProperty(true)

    @get:Bindable
    var requirePhotoProof by bindableProperty(true)

    @get:Bindable
    var takeWithFood by bindableProperty(false)

    @get:Bindable
    var canAddExtraDose by bindableProperty(false)

    @get:Bindable("asNeeded")
    val showNotificationSwitch: Boolean
        get() {
            return !asNeeded
        }

    @get:Bindable
    var showRequireProofSwitch by bindableProperty(false)

    @get:Bindable("asNeeded")
    val showSchedulingUI: Boolean
        get() {
            return !asNeeded
        }

    @get:Bindable("showSchedulingUI", "canAddExtraDose")
    val showExtraDoseButton: Boolean
        get() {
            return showSchedulingUI && canAddExtraDose
        }


    private var extraDoseItems = observableListOf<ItemExtraDose>()

    fun fillFromMedication(medicationFull: MedicationFull) {
        val medication = medicationFull.medication
        val medType = medicationFull.medicationType
        val doseUnit = medicationFull.doseUnit

        name = medication.name
        description = medication.description
        rxNumber = medication.rxNumber
        pharmacy = medication.pharmacy
        medTypeString = medType.name
        doseUnitString = doseUnit.unit
        amountPerDose = medication.amountPerDose
        remainingDoses = medication.remainingDoses
        schedule = RepeatSchedule.fromMedication(medication)
        setExtraDoses(medication.moreDosesPerDay)
        asNeeded = medication.isAsNeeded()
        notify = medication.notify
        requirePhotoProof = medication.requirePhotoProof
        takeWithFood = medication.takeWithFood
    }

    fun setupMedicationTypeInput(input: AutoCompleteTextView, types: List<MedicationType>) {
        val context = input.context
        val typesAdapter = MedTypeListAdapter2(context, types)
        input.setAdapter(typesAdapter)
        input.setOnItemClickListener { parent, view, position, id ->
            val adapter = parent.adapter as? MedTypeListAdapter2 ?: return@setOnItemClickListener
            val type = adapter.getItem(position)
            medTypeString = type.name
        }
    }

    fun setupDoseUnitInput(input: AutoCompleteTextView, types: List<DoseUnit>) {
        val context = input.context
        val typesAdapter = DoseUnitListAdapter2(context, types)
        input.setAdapter(typesAdapter)
        input.setOnItemClickListener { parent, view, position, id ->
            val adapter = parent.adapter as? DoseUnitListAdapter2 ?: return@setOnItemClickListener
            val type = adapter.getItem(position)
            doseUnitString = type.unit
        }
    }

    fun setupExtraDosesList(recyclerView: RecyclerView) {
        recyclerView.addDefaultDivider()
        BindableAdapter(extraDoseItems, BR.item).map<ItemExtraDose, ExtraDoseTemplate2Binding>(
                R.layout.extra_dose_template2) {
            onBind { holder ->
                holder.binding.deleteDoseButton.setOnClickListener {
                    holder.binding.item ?: return@setOnClickListener
                    val index = holder.bindingAdapterPosition
                    extraDoseItems.removeAt(index)
                }
                holder.binding.scheduleDoseButton.setOnClickListener {
                    val item = holder.binding.item ?: return@setOnClickListener
                    val index = holder.bindingAdapterPosition
                    onScheduleDoseTapped(index, item.schedule)
                }
            }
        }.into(recyclerView)
    }

    private fun onScheduleDoseTapped(index: Int, existingSchedule: RepeatSchedule) {
        launchVmScope({
            val result = interactor?.rescheduleDose(index, existingSchedule)
            if (result != null) {
                extraDoseItems.removeAt(index)
                val schedule = result.first
                val bcType = result.second
                if (bcType != BirthControlType.NO) {
                    val newSchedule = schedule.asBirthControl(bcType)
                    extraDoseItems.add(index, ItemExtraDose(newSchedule))
                    addBirthControlDoses(newSchedule, bcType)
                } else {
                    extraDoseItems.add(index, ItemExtraDose(schedule))
                }
            }
        })
    }

    fun clearExtraDoseItems() {
        extraDoseItems.clear()
    }

    fun setExtraDoses(doses: List<RepeatSchedule>) {
        extraDoseItems.clear()
        extraDoseItems.addAll(doses.map { ItemExtraDose(it) })
    }


    fun onExtraDoseButtonTapped() {
        val schedule = RepeatSchedule.BLANK
        extraDoseItems.add(ItemExtraDose(schedule))
    }

    fun shouldNotify(): Boolean {
        return notify && !asNeeded
    }

    fun onRepeatScheduleTapped() {
        launchVmScope({
            val result = interactor?.openSchedulePicker() ?: return@launchVmScope
            val schedule = result.first
            val bcType = result.second
            if (bcType != BirthControlType.NO) {
                this.schedule = schedule.asBirthControl(bcType)
                addBirthControlDoses(this.schedule, bcType)
            } else {
                this.schedule = schedule
            }
        })
    }

    fun getExtraSchedules(): List<RepeatSchedule> {
        return extraDoseItems.map { it.schedule }
    }

    fun areAllSchedulesValid(): Boolean {
        if (!schedule.isValid(false)) return false
        return extraDoseItems.none {
            !it.schedule.isValid(false)
        }
    }


    private fun addBirthControlDoses(schedule: RepeatSchedule, birthControlType: BirthControlType) {
        if (birthControlType == BirthControlType.NO) return
        val activeDays = birthControlType.days
        val calendar = Calendar.getInstance().also { schedule.fillCalendar(it) }
        for (i in 1..activeDays) {
            val newSchedule = RepeatSchedule(schedule.hour, schedule.minute,
                    calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.YEAR), daysBetween = activeDays + 7)
            extraDoseItems.add(ItemExtraDose(newSchedule))

            calendar.add(Calendar.DATE, 1)
        }
    }


}