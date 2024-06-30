package dev.corruptedark.diditakemymeds.util

import android.text.format.DateFormat
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.google.android.material.appbar.MaterialToolbar
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.DoseUnit
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.MedicationType
import java.util.concurrent.TimeUnit

@BindingAdapter("zeroContentInsetStart")
fun setNoContentInsetStart(view: MaterialToolbar, noContentInsetStart: Boolean) {
    if (noContentInsetStart) {
        view.setContentInsetsRelative(0, view.contentInsetEnd)
    }
}

@BindingAdapter("zeroNavigationContentInsetStart")
fun setNoNavigationContentInsetStart(
    view: MaterialToolbar,
    zeroNavigationContentInsetStart: Boolean
) {
    if (zeroNavigationContentInsetStart) {
        view.contentInsetStartWithNavigation = 0
    }
}

@BindingAdapter("medicationType")
fun setMedicationType(view: TextView, medicationType: MedicationType) {
    val text = view.context.getString(R.string.type_label_format, medicationType.name)
    view.text = text
}

@BindingAdapter("rxNumber")
fun setRxNumber(view: TextView, medication: Medication) {
    val context = view.context
    val rxNumber = if (medication.rxNumber == Medication.UNDEFINED) {
        context.getString(R.string.undefined)
    } else medication.rxNumber

    val text = view.context.getString(R.string.rx_number_label_format, rxNumber)
    view.text = text
}

@BindingAdapter("doseUnit", "amountPerDose")
fun setDoseInfo(view: TextView, doseUnit: DoseUnit, amountPerDose: Double) {
    val doseValueString = amountPerDose.toBigDecimal().stripTrailingZeros().toPlainString()
    val doseUnitString = doseUnit.unit
    val text =
        view.context.getString(R.string.dose_amount_label_format, doseValueString, doseUnitString)
    view.text = text
}

@BindingAdapter("remainingDoses")
fun setRemainingDoses(view: TextView, remainingDoses: Int) {
    val text = view.context.getString(R.string.remaining_doses_label_format, remainingDoses)
    view.text = text
}

@BindingAdapter("nextDose")
fun setNextDose(view: TextView, medication: Medication) {
    val context = view.context
    val doseString = medication.nextDoseString(context)
    val text = context.getString(R.string.next_dose_label, doseString)
    view.text = text
}

@BindingAdapter("closestDose")
fun setClosestDose(view: TextView, medication: Medication) {
    val context = view.context
    val doseString = medication.closestDoseString(context)
    val text = context.getString(R.string.next_dose_label, doseString)
    view.text = text
}

@BindingAdapter("sinceLastDose")
fun setSinceLastDose(view: TextView, medication: Medication) {
    val timeSinceTakenDose = medication.timeSinceLastTakenDose()
    val days = TimeUnit.MILLISECONDS.toDays(timeSinceTakenDose)
    val hours = TimeUnit.MILLISECONDS.toHours(timeSinceTakenDose) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeSinceTakenDose) % 60

    val text = view.context.getString(R.string.time_since_dose_template, days, hours, minutes)
    view.text = text

}

@BindingAdapter("pharmacy")
fun setPharmacy(view: TextView, medication: Medication) {
    val context = view.context
    val pharmacy = if (medication.pharmacy == Medication.UNDEFINED) {
        context.getString(R.string.undefined)
    } else medication.pharmacy

    val text = view.context.getString(R.string.pharmacy_label_format, pharmacy)
    view.text = text
}

@BindingAdapter("justTookIt")
fun setJustTookIt(view: TextView, medication: Medication) {
    val text = if (medication.closestDoseAlreadyTaken() && !medication.isAsNeeded()) {
        view.context.getString(R.string.took_this_already)
    } else {
        view.context.getString(R.string.i_just_took_it)
    }
    view.text = text
}


@BindingAdapter("timeTaken")
fun setTimeTaken(view: TextView, doseRecord: DoseRecord) {
    val context = view.context
    val doseString = medicationDoseString(context, doseRecord.doseTime)
    val text = context.getString(R.string.time_taken, doseString)
    view.text = text
}

@BindingAdapter("closestDose")
fun setClosestDose(view: TextView, doseRecord: DoseRecord) {
    val context = view.context
    val doseString = medicationDoseString(context, doseRecord.closestDose)
    val text = context.getString(R.string.closest_dose_label, doseString)
    view.text = text
}


@BindingAdapter("nextDose")
fun setNextDose(view: TextView, doseRecord: DoseRecord) {
    val context = view.context
    val doseString = medicationDoseString(context, doseRecord.closestDose)
    val text = context.getString(R.string.next_dose_label, doseString)
    view.text = text
}

@BindingAdapter("timeFromMillis")
fun setTimeFromMillis(view: TextView, millis: Long) {
    val context = view.context
    val systemIs24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = if (systemIs24Hour) context.getString(R.string.time_24) else context.getString(R.string.time_12)
    val text = DateFormat.format(timeFormat, millis)
    view.text = text
}
@BindingAdapter("dateFromMillis")
fun setDateFromMillis(view: TextView, millis: Long) {
    val context = view.context
    val dateFormat = context.getString(R.string.date_format)
    val text = DateFormat.format(dateFormat, millis)
    view.text = text
}