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

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.getSystemService
import androidx.core.view.iterator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jakewharton.processphoenix.ProcessPhoenix
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.activityresult.createDocument
import com.siravorona.utils.activityresult.getContent
import com.siravorona.utils.activityresult.requestPermission
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import com.siravorona.utils.getThemeDrawableByAttr
import com.siravorona.utils.permissions.isPermissionGranted
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
import dev.corruptedark.diditakemymeds.settings.AppSettings
import dev.corruptedark.diditakemymeds.util.applyThemeFromPreferences
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
                ActivityMainBinding::class, BR.vm) {
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
        applyThemeFromPreferences()
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

    override fun onResume() {
        super.onResume()
        mainScope.launch {
            requestPermissions()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        lifecycleScope.launch(lifecycleDispatcher) {
            sortType = AppSettings.sortBy

            if (BuildConfig.VERSION_CODE > AppSettings.lastVersionUsed) {
                ensureAlarmsScheduled()
            }
            AppSettings.lastVersionUsed = BuildConfig.VERSION_CODE

            val medId = intent.getLongExtra(getString(R.string.med_id_key),
                    Medication.INVALID_MED_ID)
            val takeMed = intent.getBooleanExtra(getString(R.string.take_med_key), false)
            if (medicationDao(context).medicationExists(medId)) {
                openMedDetailActivity(medId, takeMed)
            }
        }
    }

    private fun ensureAlarmsScheduled() {
        medicationDao(context).getAllRaw().forEach { medication ->
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

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        adjustMenuItemsVisibility(menu)
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

            R.id.theme_light -> {
                switchToLightTheme()
                true
            }

            R.id.theme_dark -> {
                switchToDarkTheme()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private suspend fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPermissionGranted(android.Manifest.permission.POST_NOTIFICATIONS)) {
                ActivityResultManager.getInstance().requestPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService<AlarmManager>()
            if (alarmManager != null) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    ActivityResultManager.getInstance().requestResult(ActivityResultContracts.StartActivityForResult(), Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
        }
    }


    private fun adjustMenuItemsVisibility(menu: Menu) {
        val isLightTheme = AppSettings.isLightTheme
        menu.iterator().forEach { menuItem ->
            val id = menuItem.itemId
            if (id == R.id.theme_light) {
                menuItem.isVisible = !isLightTheme
            }
            if (id == R.id.theme_dark) {
                menuItem.isVisible = isLightTheme
            }
        }
    }

    private fun switchToLightTheme() {
        AppSettings.isLightTheme = true
        ProcessPhoenix.triggerRebirth(this)
    }

    private fun switchToDarkTheme() {
        AppSettings.isLightTheme = false
        ProcessPhoenix.triggerRebirth(this)
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
        this.sortType = nextSortType
        AppSettings.sortBy = sortType
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
            val restoreUri = ActivityResultManager.getInstance().getContent(MIME_TYPES)
                    ?: return@launch
            restoreJob = lifecycleScope.launch(lifecycleDispatcher) {
                doRestore(this@MainActivity, restoreUri)
            }
        }
    }


    private fun backUpDatabase() {
        // Intent.normalizeMimeType(MIME_TYPES), intent.addCategory(Intent.CATEGORY_OPENABLE)
        lifecycleScope.launch(lifecycleDispatcher) {
            val suggestedName = StorageManager.suggestBackupFileName()
            val backupUri = ActivityResultManager.getInstance()
                    .createDocument(MIME_TYPES, suggestedName) ?: return@launch
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
                        Toast.makeText(context, getString(R.string.database_restored),
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure { exception ->
                exception.printStackTrace()
                mainScope.launch {
                    launchMedicationsObserve()
                    Toast.makeText(context, getString(R.string.database_is_invalid),
                            Toast.LENGTH_SHORT).show()
                }
            }
        }

        mainScope.launch {
            launchMedicationsObserve()
        }
    }

    private suspend fun tryRestoreDb(restoreUri: Uri, context: Context) {
        cancelJob(medicationsFlowJob)
        withContext(Dispatchers.IO) {
            runCatching {
                StorageManager.tryRestoreDb(context, restoreUri)
            }.onFailure { exception ->
                exception.printStackTrace()
                mainScope.launch {
                    Toast.makeText(applicationContext, getString(R.string.database_is_invalid),
                            Toast.LENGTH_SHORT).show()
                    launchMedicationsObserve()
                }
            }.onSuccess {
                mainScope.launch {
                    launchMedicationsObserve {
                        Toast.makeText(applicationContext, getString(R.string.database_restored),
                                Toast.LENGTH_SHORT).show()
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
            medicationsFlow = MedicationDB.getInstance(this@MainActivity).medicationDao()
                    .observeAllFullDistinct()
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                medicationsFlow?.collectLatest { medications ->
                    mainScope.launch {
                        vm.setMedications(medications, sortType)
                        binding.listEmptyLabel.visibility = if (medications.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        additionalAction?.invoke()
    }
}