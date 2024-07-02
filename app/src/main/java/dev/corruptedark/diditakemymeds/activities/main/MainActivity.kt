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

package dev.corruptedark.diditakemymeds.activities.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.activityresult.createDocument
import com.siravorona.utils.activityresult.getContent
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import com.siravorona.utils.getThemeDrawableByAttr
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.BuildConfig
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.StorageManager
import dev.corruptedark.diditakemymeds.activities.AboutActivity
import dev.corruptedark.diditakemymeds.activities.add_edit_med.AddEditMedActivity
import dev.corruptedark.diditakemymeds.activities.meddetails.MedDetailActivity
import dev.corruptedark.diditakemymeds.data.db.MedicationDB
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.ActivityMainBinding
import dev.corruptedark.diditakemymeds.util.notifications.AlarmIntentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity :
    BaseBoundInteractableVmActivity<ActivityMainBinding, MainViewModel, MainViewModel.Interactor>(
        ActivityMainBinding::class, BR.vm
    ) {
    private var sortType = SortBy.NAME
    private val MIME_TYPES = "application/*"

    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val context = this
    private val mainScope = MainScope()
    @Volatile
    private var restoreJob: Job? = null
    @Volatile
    private var backupJob: Job? = null

    private var medicationsFlow: Flow<List<MedicationFull>>? = null
    private var medicationsFlowJob: Job? = null

    override val vm: MainViewModel by viewModels()
    override val modelInteractor = object : MainViewModel.Interactor {
        override fun openMedication(medication: Medication) {
            this@MainActivity.openMedDetailActivity(medication.id, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.appbar.toolbar)
        val logo = getThemeDrawableByAttr(R.attr.ditmm_bar_logo)
        binding.appbar.toolbar.logo = logo
        supportActionBar?.title = getString(R.string.app_name)

        binding.addMedButton.setOnClickListener {
            openAddMedActivity()
        }

        vm.setupMedicationsRecycler(binding.medsRecycler)
        mainScope.launch {
            launchMedicationsObserve()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        lifecycleScope.launch(lifecycleDispatcher) {

            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            val sortKey = sharedPref.getString(getString(R.string.sort_key), "") ?: ""
            sortType = SortBy.getByKeyOrDefault(sortKey, SortBy.TIME)

            // recreate alarms that were wiped when the app process was killed
            // thanks, Google
            ensureAlarmsScheduled()

            with(sharedPref.edit()) {
                putInt(getString(R.string.last_version_used_key), BuildConfig.VERSION_CODE)
                apply()
            }

            val medId =
                intent.getLongExtra(getString(R.string.med_id_key), Medication.INVALID_MED_ID)
            val takeMed = intent.getBooleanExtra(getString(R.string.take_med_key), false)
            if (medicationDao(context).medicationExists(medId)) {
                openMedDetailActivity(medId, takeMed)
            }
        }
    }

    private fun ensureAlarmsScheduled() {
        medicationDao(context).getAllRaw()
            .forEach { medication ->
                if (medication.notify) {
                    scheduleMedicationAlarm(medication)
                }
            }
    }

    private fun scheduleMedicationAlarm(medication: Medication) {
        AlarmIntentManager.scheduleMedicationAlarm(this, medication)
    }

    private fun openMedDetailActivity(medId: Long, takeMed: Boolean) {
        MedDetailActivity.start(this, medId, takeMed)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.info -> {
                openAboutActivity()
                true
            }
            R.id.sortType -> {
                onSortMenuItemTapped(item)
                true
            }
            R.id.restore_database -> {
                restoreDatabase()
                true
            }
            R.id.back_up_database -> {
                backUpDatabase()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSortMenuItemTapped(item: MenuItem) {
        val nextSort: SortBy
        val iconId: Int
        when (sortType) {
            SortBy.TIME -> {
                nextSort = SortBy.NAME
                iconId = R.drawable.ic_sort_by_alpha
            }
            SortBy.NAME -> {
                nextSort = SortBy.TYPE
                iconId = R.drawable.ic_sort_by_type
            }
            SortBy.TYPE -> {
                nextSort = SortBy.TIME
                iconId = R.drawable.ic_sort_by_time
            }
        }
        item.icon = AppCompatResources.getDrawable(this, iconId)
        sortAndNotify(sortType, nextSort)
    }

    private fun sortAndNotify(sortType: SortBy, nextSortType: SortBy) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        this.sortType = nextSortType
        with(sharedPref.edit()) {
            putString(getString(R.string.sort_key), sortType.key)
            apply()
        }
        vm.sortMedications(sortType)
    }

    private fun openAboutActivity() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun openAddMedActivity() {
        lifecycleScope.launch {
            AddEditMedActivity.addMedication(this@MainActivity)
        }
    }

    private fun restoreDatabase() {
        lifecycleScope.launch(lifecycleDispatcher) {
            val restoreUri =
                ActivityResultManager.getInstance().getContent(MIME_TYPES) ?: return@launch
            restoreJob = lifecycleScope.launch(lifecycleDispatcher) {
                doRestore(this@MainActivity, restoreUri)
            }
        }
    }


    private fun backUpDatabase() {
        // Intent.normalizeMimeType(MIME_TYPES), intent.addCategory(Intent.CATEGORY_OPENABLE)
        lifecycleScope.launch(lifecycleDispatcher) {
            val suggestedName = StorageManager.suggestBackupFileName()
            val backupUri =
                ActivityResultManager.getInstance().createDocument(MIME_TYPES, suggestedName)
                    ?: return@launch
            doBackup(context, backupUri)
        }
    }

    private suspend fun cancelJob(job: Job?) {
        runCatching {
            job?.cancelAndJoin()
        }.onFailure { throwable ->
            throwable.printStackTrace()
        }
    }


    private suspend fun doRestore(context: Context, restoreUri: Uri) {
        if (MedicationDB.databaseFileIsValid(context, restoreUri)) {
            doRestoreDb(context, restoreUri)
        } else {
            tryRestoreDb(restoreUri, context)
        }
    }

    private suspend fun doBackup(context: Context, backupUri: Uri) {
        cancelJob(medicationsFlowJob)
        runCatching {
            StorageManager.backup(context, backupUri)
        }.onFailure { exception ->
            exception.printStackTrace()
            mainScope.launch {
                Toast.makeText(context, getString(R.string.back_up_failed), Toast.LENGTH_SHORT)
                    .show()
                launchMedicationsObserve()
            }
        }.onSuccess {
            mainScope.launch {
                Toast.makeText(context, getString(R.string.back_up_successful), Toast.LENGTH_SHORT)
                    .show()
                launchMedicationsObserve()
            }
        }
    }

    private suspend fun doRestoreDb(context: Context, restoreUri: Uri) {
        cancelJob(medicationsFlowJob)

        withContext(Dispatchers.IO) {
            runCatching {
                StorageManager.restore(context, restoreUri)
            }.onSuccess {
                mainScope.launch {
                    launchMedicationsObserve {
                        Toast.makeText(
                            context, getString(R.string.database_restored), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.onFailure { exception ->
                exception.printStackTrace()
                mainScope.launch {
                    launchMedicationsObserve()
                    Toast.makeText(
                        context, getString(R.string.database_is_invalid), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        mainScope.launch {
            launchMedicationsObserve()
        }
    }

    private suspend fun tryRestoreDb(
        restoreUri: Uri,
        context: Context
    ) {
        cancelJob(medicationsFlowJob)
        withContext(Dispatchers.IO) {
            runCatching {
                StorageManager.tryRestoreDb(context, restoreUri)
            }.onFailure { exception ->
                exception.printStackTrace()
                mainScope.launch {
                    Toast.makeText(
                        applicationContext, getString(R.string.database_is_invalid),
                        Toast.LENGTH_SHORT
                    ).show()
                    launchMedicationsObserve()
                }
            }.onSuccess {
                mainScope.launch {
                    launchMedicationsObserve {
                        Toast.makeText(
                            applicationContext, getString(R.string.database_restored),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private suspend fun launchMedicationsObserve(additionalAction: (() -> Unit)? = null) {
        restoreJob?.join()
        backupJob?.join()
        if (medicationsFlowJob?.isActive == true) {
            cancelJob(medicationsFlowJob)
        }
        medicationsFlowJob = lifecycleScope.launch {
            medicationsFlow = MedicationDB.getInstance(this@MainActivity)
                .medicationDao()
                .observeAllFullDistinct()
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                medicationsFlow?.collectLatest { medications ->
                    mainScope.launch {
                        vm.setMedications(medications, sortType)
                        binding.listEmptyLabel.visibility =
                            if (medications.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        additionalAction?.invoke()
    }
}