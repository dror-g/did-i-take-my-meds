/*
 * Did I Take My Meds? is a FOSS app to keep track of medications
 * Did I Take My Meds? is designed to help prevent a user from skipping doses and/or overdosing
 *     Copyright (C) 2021  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Did I Take My Meds? is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Did I Take My Meds? is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.diditakemymeds.activities.meddetails

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.activityresult.takePicture
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.StorageManager
import dev.corruptedark.diditakemymeds.activities.EditMedActivity
import dev.corruptedark.diditakemymeds.activities.add_edit_med.AddEditMedActivity
import dev.corruptedark.diditakemymeds.activities.dosedetails.DoseDetailActivity
import dev.corruptedark.diditakemymeds.data.db.MedicationDB
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.db.proofImageDao
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.ProofImage
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.ActivityMedDetailBinding
import dev.corruptedark.diditakemymeds.util.ActionReceiver
import dev.corruptedark.diditakemymeds.util.AlarmIntentManager
import dev.corruptedark.diditakemymeds.util.DialogUtil
import dev.corruptedark.diditakemymeds.util.medicationDoseString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.Executors

class MedDetailActivity : BaseBoundInteractableVmActivity<ActivityMedDetailBinding, MedDetailViewModel, MedDetailViewModel.Interactor>(
    ActivityMedDetailBinding::class, BR.vm) {
    private var closestDose: Long = -1L
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var alarmManager: AlarmManager? = null
    private var alarmIntent: PendingIntent? = null
    private val context = this
    private val mainScope = MainScope()

    private var takeMed = false
    private var medicationId = 0L

    override val vm: MedDetailViewModel by viewModels()
    override val modelInteractor = object : MedDetailViewModel.Interactor {
        override fun openDoseDetail(medication: Medication, doseRecord: DoseRecord) {
            this@MedDetailActivity.openDoseDetail(medication, doseRecord)
        }

        override fun promptDeleteDoseRecord(medication: Medication, doseRecord: DoseRecord) {
            this@MedDetailActivity.promptDeleteDoseRecord(medication, doseRecord)
        }

        override fun justTookItPressed(medication: Medication) {
            justTookItButtonPressed()
        }

        override fun saveMedication(medication: Medication) {
            updateMedication(medication)
        }

        override fun scheduleNextMedicationAlarm(medication: Medication) {
            this@MedDetailActivity.scheduleNextMedicationAlarm(medication)
        }

        override fun cancelMedicationAlarm(medication: Medication) {
            this@MedDetailActivity.cancelExistingMedicationAlarm(medication)
        }
    }
    private var medicationFlow: Flow<MedicationFull>? = null

    // region lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        takeMed = intent.getBooleanExtra(EXTRA_TAKE_MED, false)
        medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appbar.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        ViewCompat.setNestedScrollingEnabled(binding.outerScroll, true)
        vm.setupDoseRecordsList(binding.dosesRecycler)

        lifecycleScope.launch {
            medicationFlow = MedicationDB.getInstance(this@MedDetailActivity)
                .medicationDao()
                .observeFullDistinct(medicationId)
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                medicationFlow?.collectLatest { medFull ->
                    medFull.medication.let {
                        it.updateStartsToFuture()
                        it.doseRecord.sort()
                    }
                    mainScope.launch {
                        onMedicationChanged(medFull)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(lifecycleDispatcher) {
            checkMedicationExists()
        }
    }

    // endregion

    // region options menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.med_detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.delete -> {
                promptDeleteMedication(vm.medication)
                true
            }
            R.id.edit -> {
                lifecycleScope.launch(lifecycleDispatcher) {
                    openEditMedication(vm.medication)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // endregion

    // region medication manipulation
    private fun updateMedication(medication: Medication) {
        lifecycleScope.launch(lifecycleDispatcher) {
            medicationDao(context)
                .updateMedications(medication)
        }
    }

    private fun deleteMedication(medication: Medication) {
        lifecycleScope.launch(lifecycleDispatcher) {
            val db = MedicationDB.getInstance(context)
            cancelExistingMedicationAlarm(medication, false)
            withContext(Dispatchers.IO) {
                val proofImages =
                    db.proofImageDao().getProofImagesByMedId(medication.id)
                proofImages.forEach { proofImage ->
                    try {
                      StorageManager.deleteImageFile(this@MedDetailActivity, proofImage)
                    } catch (e: Exception) {
                        //
                    }
                    db.proofImageDao().delete(proofImage)
                }

                db.medicationDao().delete(medication)
            }
            finish()
        }
    }
    private fun promptDeleteMedication(medication: Medication) {
        lifecycleScope.launch(lifecycleDispatcher) {
            val action = DialogUtil.showMaterialDialogSuspend(this@MedDetailActivity, this@MedDetailActivity,
                title = getString(R.string.are_you_sure),
                message = getString(R.string.medication_delete_warning),
                positiveButtonText = getString(R.string.confirm)
            )
            when(action) {
                DialogUtil.Action.POSITIVE -> {
                    deleteMedication(medication)
                }
                else -> {}
            }
        }
    }
    // endregion

    // region alarms
    private fun updateAlarm(medication: Medication) {
        if (medication.notify) {
            cancelExistingMedicationAlarm(medication, false)
            scheduleNextMedicationAlarm(medication, false)
        } else {
            //Cancel alarm
            cancelExistingMedicationAlarm(medication, false)
        }
    }

    private fun cancelExistingMedicationAlarm(medication: Medication, showToast: Boolean = true) {
        alarmIntent?.let { alarmManager?.cancel(it) }
        if (showToast) {
            Toast.makeText(
                context,
                getString(R.string.notifications_disabled),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun scheduleNextMedicationAlarm(medication: Medication, showToast: Boolean = true) {
        cancelExistingMedicationAlarm(medication, false)
        val alarmIntent = AlarmIntentManager.buildNotificationAlarm(context, medication)
        this.alarmIntent = alarmIntent
        AlarmIntentManager.setExact(
            alarmManager,
            alarmIntent,
            medication.calculateNextDose().timeInMillis
        )

        val receiver = ComponentName(context, ActionReceiver::class.java)
        context.packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        if (showToast) {
            Toast.makeText(
                context,
                getString(R.string.notifications_enabled),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // endregion

    private fun openDoseDetail(medication: Medication, doseRecord: DoseRecord) {
        DoseDetailActivity.open(context, medication.id, doseRecord)
    }

    // region dose manipulation
    private fun createDose(medication: Medication): DoseRecord {
        val calendar = Calendar.getInstance()
        return if (medication.isAsNeeded()) {
            DoseRecord(calendar.timeInMillis)
        } else {
            DoseRecord(
                calendar.timeInMillis,
                medication.calculateClosestDose().timeInMillis
            )
        }
    }

    private fun saveDose(newDose: DoseRecord) {
        val medication = vm.medication
        medication.addNewTakenDose(newDose)
        vm.notifyMedicationPropertyChange()

        lifecycleScope.launch(lifecycleDispatcher) {
            val db = MedicationDB.getInstance(context)
            db.medicationDao().updateMedications(medication)
            with(NotificationManagerCompat.from(context.applicationContext)) {
                cancel(medication.id.toInt())
            }
        }
    }


    private fun promptDeleteDoseRecord(medication: Medication, doseRecord: DoseRecord) {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.are_you_sure))
            .setMessage(
                getString(R.string.dose_record_delete_warning) + "\n\n" + medicationDoseString(context, doseRecord.doseTime)
            )
            .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val realDoseRecordDialogBuilder = MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.was_this_dose_really_taken))
                    .setMessage(getString(R.string.remaining_doses_correction_message))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        removeDose(medication, doseRecord, realDose = true)
                    }
                    .setNegativeButton(getString(R.string.no)) { _, _ ->
                        removeDose(medication, doseRecord, realDose = false)
                    }
                    .setCancelable(false)
                realDoseRecordDialogBuilder.show()

            }

        dialogBuilder.show()
    }
    private fun removeDose(medication: Medication, doseRecord: DoseRecord, realDose: Boolean) {
        lifecycleScope.launch(lifecycleDispatcher) {
            val medId = medication.id
            val doseTime = doseRecord.doseTime

            if (proofImageDao(context).proofImageExists(medId, doseTime)) {
                val proofImage = proofImageDao(context).get(medId, doseTime)
                if (proofImage != null) {
                    StorageManager.deleteImageFile(this@MedDetailActivity, proofImage)
                    proofImageDao(context).delete(proofImage)
                }
            }

            medication.removeTakenDose(doseRecord, realDose)
            medicationDao(context).updateMedications(medication)
        }
    }
    // endregion

    @Synchronized
    private fun checkMedicationExists() {
        if (!medicationDao(this).medicationExists(medicationId)) {
            mainScope.launch {
                onBackPressed()
            }
        }
    }

    private fun takeImageProof(medId: Long, doseTime: Long) {
        lifecycleScope.launch(lifecycleDispatcher) {
            if (Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packageManager) == null) {
                return@launch // can't take photos
            }
            val photoFile = withContext(Dispatchers.IO) {
                runCatching { StorageManager.createImageFile(context, medId, doseTime) }
            }.getOrNull() ?: return@launch // couldn't create file to hold the image

            val photoURI = FileProvider.getUriForFile(
                context,
                getString(R.string.file_provider),
                photoFile
            )
            val picTaken = ActivityResultManager.getInstance().takePicture(photoURI)
            saveImageProof(picTaken, photoFile.name)
        }
    }


    private suspend fun openEditMedication(aMedication: Medication) {
      AddEditMedActivity.startForResult(this, aMedication)
    }

    private fun onMedicationChanged(medicationFull: MedicationFull) {
        vm.medicationFull = medicationFull
        if (!medicationFull.medication.isAsNeeded()) {
            closestDose = medicationFull.medication.calculateClosestDose().timeInMillis
        }
        updateAlarm(medicationFull.medication)

    }

    private fun justTookItButtonPressed() {
        val medication = vm.medication
        if (medication.active) {
            medication.updateStartsToFuture()
            if (medication.closestDoseAlreadyTaken() && !medication.isAsNeeded()) {
                Toast.makeText(this, getString(R.string.already_took_dose), Toast.LENGTH_SHORT)
                    .show()
            }
            else if (!medication.hasDoseRemaining()) {
                Toast.makeText(this, getString(R.string.no_remaining_doses_message), Toast.LENGTH_LONG).show()
            }
            else {
                if (medication.requirePhotoProof) {
                    takeImageProof(medication.id, System.currentTimeMillis())
                } else {
                    saveDose(createDose(medication))
                }
            }
        }
    }

    private fun saveImageProof(pictureTaken: Boolean, filePath: String) {
        val medication = vm.medication
        if (pictureTaken) {
            val dose = createDose(medication)
            val proofImage = ProofImage(medication.id, dose.doseTime, filePath)
            saveDose(dose)
            lifecycleScope.launch (lifecycleDispatcher) {
                proofImageDao(context).insertAll(proofImage)
                mainScope.launch {
                    Toast.makeText(context, getString(R.string.dose_and_proof_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
        else {
            mainScope.launch {
                Toast.makeText(context, getString(R.string.failed_to_get_proof), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val EXTRA_MEDICATION_ID = "med_id"
        private const val EXTRA_TAKE_MED = "take_med"
        fun start(context: Context, medicationId: Long, takeMed: Boolean) {
            val intent = Intent(context, MedDetailActivity::class.java).apply {
                putExtra(EXTRA_MEDICATION_ID, medicationId)
                putExtra(EXTRA_TAKE_MED, takeMed)
            }
            context.startActivity(intent)
        }
    }
}