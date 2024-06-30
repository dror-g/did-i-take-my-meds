package dev.corruptedark.diditakemymeds.data.models.joins

import androidx.room.Embedded
import androidx.room.Relation
import dev.corruptedark.diditakemymeds.data.models.DoseUnit
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.MedicationType

data class MedicationFull(
    @Embedded val medication: Medication,
    @Relation(parentColumn = "typeId", entityColumn = "id")
    val medicationType: MedicationType,
    @Relation(parentColumn = "doseUnitId", entityColumn = "id")
    val doseUnit: DoseUnit
) {
    companion object {
        val BLANK = MedicationFull(Medication.BLANK, MedicationType(""), DoseUnit(""))
    }
}