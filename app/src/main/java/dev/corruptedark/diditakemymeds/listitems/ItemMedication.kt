package dev.corruptedark.diditakemymeds.listitems

import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.MedicationType

data class ItemMedication(val medication: Medication, val medicationType: MedicationType) {

}