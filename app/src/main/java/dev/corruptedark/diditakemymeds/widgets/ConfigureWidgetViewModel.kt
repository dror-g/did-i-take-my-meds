package dev.corruptedark.diditakemymeds.widgets

import androidx.recyclerview.widget.RecyclerView
import com.siravorona.utils.base.InteractableViewModel
import com.siravorona.utils.listadapters.BindableAdapter
import com.siravorona.utils.lists.observableListOf
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.main.ItemMedication
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.MedListItem2Binding
import dev.corruptedark.diditakemymeds.util.addDefaultDivider

class ConfigureWidgetViewModel : InteractableViewModel<ConfigureWidgetViewModel.Interactor>() {
    interface Interactor {
        fun onMedicationTapped(medication: Medication)
    }

    private val medicationItems = observableListOf<ItemMedication>()

    fun initRecycler(recyclerView: RecyclerView) {
        recyclerView.addDefaultDivider()
        BindableAdapter(medicationItems, BR.item)
                .map<ItemMedication, MedListItem2Binding>(R.layout.med_list_item2) {
                    onClick {
                        val item = it.binding.item ?: return@onClick
                        interactor?.onMedicationTapped(item.medication)
                    }
                }.into(recyclerView)
    }

    fun setMedications(medications: List<MedicationFull>) {
        medicationItems.clear()
        val newItems = medications.map { ItemMedication(it) }
        medicationItems.addAll(newItems)
    }
}