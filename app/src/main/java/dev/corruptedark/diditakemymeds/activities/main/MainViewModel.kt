package dev.corruptedark.diditakemymeds.activities.main

import androidx.recyclerview.widget.RecyclerView
import com.siravorona.utils.base.InteractableViewModel
import com.siravorona.utils.listadapters.BindableAdapter
import com.siravorona.utils.lists.observableListOf
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.MedListItem2Binding

class MainViewModel : InteractableViewModel<MainViewModel.Interactor>() {
    interface Interactor {
        fun openMedication(medication: Medication)
    }

    private val medicationItems = observableListOf<ItemMedication>()

    fun setMedications(medications: List<MedicationFull>, sortBy: SortBy) {
        medicationItems.clear()
        val newItems = medications.map { ItemMedication(it) }
                .sortedWith(getMedicationSortComparator(sortBy))
        medicationItems.addAll(newItems)
    }

    fun setupMedicationsRecycler(recyclerView: RecyclerView) {
        BindableAdapter(medicationItems, BR.item).map<ItemMedication, MedListItem2Binding>(
                R.layout.med_list_item2) {
            onClick {
                val item = it.binding.item ?: return@onClick
                interactor?.openMedication(item.medication)
            }
        }.into(recyclerView)
    }

    fun sortMedications(sortBy: SortBy) {
        medicationItems.sortWith(getMedicationSortComparator(sortBy))
    }

    private fun getMedicationSortComparator(sortType: SortBy): Comparator<ItemMedication> {
        return when (sortType) {
            SortBy.TIME -> Comparator { a, b ->
                Medication.compareByType(a.medication, b.medication)
            }

            SortBy.NAME -> Comparator { a, b ->
                Medication.compareByName(a.medication, b.medication)
            }

            SortBy.TYPE -> Comparator { a, b ->
                Medication.compareByTime(a.medication, b.medication)
            }
        }
    }
}