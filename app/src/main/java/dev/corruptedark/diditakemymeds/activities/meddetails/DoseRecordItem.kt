package dev.corruptedark.diditakemymeds.activities.meddetails

import dev.corruptedark.diditakemymeds.data.models.DoseRecord

class DoseRecordItem(val doseRecord: DoseRecord) {
    val showClosestDose = !doseRecord.isAsNeeded()
}