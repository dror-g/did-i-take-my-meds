package dev.corruptedark.diditakemymeds.activities.main

import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull

class ItemMedication(val medicationFull: MedicationFull) {
    val medication = medicationFull.medication
    val type = medicationFull.medicationType
    val name = medication.name
}