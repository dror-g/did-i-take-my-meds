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
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.activityresult.takePicture
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.DoseDetailActivity
import dev.corruptedark.diditakemymeds.activities.EditMedActivity
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
import dev.corruptedark.diditakemymeds.util.medicationDoseString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors

class MedDetailActivity : BaseBoundInteractableVmActivity<ActivityMedDetailBinding, MedDetailViewModel, MedDetailViewModel.Interactor>(
    ActivityMedDetailBinding::class, BR.vm) {
    private var closestDose: Long = -1L
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var alarmManager: AlarmManager? = null
    private lateinit var alarmIntent: PendingIntent
    private val context = this
    private val mainScope = MainScope()

    private val IMAGE_NAME_SEPARATOR = "_"
    private val IMAGE_EXTENSION = ".jpg"
    private var imageFolder: File? = null
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
            this@MedDetailActivity.cancelMedicationAlarm(medication)
        }
    }
    private var medicationFlow: Flow<MedicationFull>? = null

    // region lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        takeMed = intent.getBooleanExtra(EXTRA_TAKE_MED, false)
        medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        imageFolder = File(filesDir.path + File.separator + getString(R.string.image_path))
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
            alarmManager?.cancel(alarmIntent)
            withContext(Dispatchers.IO) {
                val proofImages =
                    db.proofImageDao().getProofImagesByMedId(medication.id)
                proofImages.forEach { proofImage ->

                    imageFolder?.apply {
                        proofImage.deleteImageFile(imageFolder!!)
                    }
                    db.proofImageDao().delete(proofImage)

                }

                db.medicationDao().delete(medication)
            }
            finish()
        }
    }
    private fun promptDeleteMedication(medication: Medication): Boolean {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.are_you_sure))
            .setMessage(getString(R.string.medication_delete_warning))
            .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.confirm)) { dialog, which ->
                deleteMedication(medication)

            }
            .show()
        return true
    }
    // endregion

    // region alarms
    private fun cancelMedicationAlarm(medication: Medication) {
        alarmManager?.cancel(alarmIntent)
        Toast.makeText(
            context,
            getString(R.string.notifications_disabled),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun scheduleNextMedicationAlarm(medication: Medication) {
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
        Toast.makeText(
            context,
            getString(R.string.notifications_enabled),
            Toast.LENGTH_SHORT
        ).show()
    }
    // endregion

    private fun openDoseDetail(medication: Medication, doseRecord: DoseRecord) {
        val intent = Intent(context, DoseDetailActivity::class.java)
        intent.putExtra(getString(R.string.med_id_key), medication.id)
        intent.putExtra(getString(R.string.dose_time_key), doseRecord.doseTime)
        intent.putExtra(getString(R.string.time_taken_key), doseRecord.closestDose)
        startActivity(intent)
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
                    imageFolder?.apply {
                        proofImage.deleteImageFile(imageFolder!!)
                    }
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



    @Throws(IOException::class)
    private fun createImageFile(medId: Long, doseTime: Long): File {
        val medIdString = medId.toString()
        val doseTimeString = doseTime.toString()
        val storageDir = imageFolder
        if (storageDir != null && !storageDir.exists()) {
            try {
                storageDir.mkdir()
            }
            catch (exception: SecurityException) {
                exception.printStackTrace()
            }
        }
        return File.createTempFile(
            medIdString + IMAGE_NAME_SEPARATOR + doseTimeString,
            IMAGE_EXTENSION,
            storageDir
        )
    }

    private fun takeImageProof(medId: Long, doseTime: Long) {
        lifecycleScope.launch(lifecycleDispatcher) {
            if (Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packageManager) == null) {
                return@launch // can't take photos
            }
            val photoFile = withContext(Dispatchers.IO) {
                runCatching { createImageFile(medId, doseTime) }
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
       EditMedActivity.startForResult(this, aMedication)
    }

    private fun onMedicationChanged(medicationFull: MedicationFull) {
        mainScope.launch {
            vm.medicationFull = medicationFull
            if (!medicationFull.medication.isAsNeeded()) {
                closestDose = medicationFull.medication.calculateClosestDose().timeInMillis
            }
            scheduleAlarm(medicationFull.medication)
        }
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


    private fun scheduleAlarm(medication: Medication) {
        alarmIntent = AlarmIntentManager.buildNotificationAlarm(context, medication)

        if (medication.notify) {
            //Set alarm
            alarmManager?.cancel(alarmIntent)

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
        } else {
            //Cancel alarm
            alarmManager?.cancel(alarmIntent)
        }
    }
    companion object {
        private const val EXTRA_MEDICATION_ID = "med_id"
        private const val EXTRA_TAKE_MED = "take_med"
        fun start(launcherActivity: ComponentActivity, medicationId: Long, takeMed: Boolean) {
            val intent = Intent(launcherActivity, MedDetailActivity::class.java).apply {
                putExtra(EXTRA_MEDICATION_ID, medicationId)
                putExtra(EXTRA_TAKE_MED, takeMed)
            }
            launcherActivity.startActivity(intent)
        }
    }
}