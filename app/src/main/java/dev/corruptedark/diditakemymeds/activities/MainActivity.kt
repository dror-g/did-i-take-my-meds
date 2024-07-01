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

package dev.corruptedark.diditakemymeds.activities

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.activityresult.createDocument
import com.siravorona.utils.activityresult.getContent
import com.siravorona.utils.base.BaseBoundActivity
import dev.corruptedark.diditakemymeds.util.AlarmIntentManager
import dev.corruptedark.diditakemymeds.listadapters.MedListAdapter
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.BuildConfig
import dev.corruptedark.diditakemymeds.activities.meddetails.MedDetailActivity
import dev.corruptedark.diditakemymeds.util.ZipFileManager
import dev.corruptedark.diditakemymeds.data.db.MedicationDB
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.db.medicationTypeDao
import dev.corruptedark.diditakemymeds.databinding.ActivityMainBinding
import com.siravorona.utils.getThemeDrawableByAttr
import kotlinx.coroutines.*
import java.io.File
import java.util.Comparator
import java.util.concurrent.Executors

class MainActivity : BaseBoundActivity<ActivityMainBinding>(
    ActivityMainBinding::class
) {
    private lateinit var sortType: String
    private val TIME_SORT = "time"
    private val NAME_SORT = "name"
    private val TYPE_SORT = "type"
    private val FOOTER_PADDING_DP = 100.0F
    private val MAXIMUM_DELAY = 60000L // 1 minute in milliseconds
    private val MINIMUM_DELAY = 1000L // 1 second in milliseconds
    private val MIME_TYPES = "application/*"
    @Volatile private var medications: MutableList<Medication>? = null
    private var medicationListAdapter: MedListAdapter? = null

    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val context = this
    private val mainScope = MainScope()
    private var refreshJob: Job? = null
    @Volatile private var restoreJob: Job? = null
    @Volatile private var backupJob: Job? = null

    private val tempDir by lazy { File(filesDir.path + File.separator + getString(R.string.temp_dir)) }
    private val imageDir by lazy { File(filesDir.path + File.separator + getString(R.string.image_path)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.appbar.toolbar)
        val logo = getThemeDrawableByAttr(R.attr.ditmm_bar_logo)
        binding.appbar.toolbar.logo = logo
        supportActionBar?.title = getString(R.string.app_name)

        binding.addMedButton.setOnClickListener {
            openAddMedActivity()
        }

        // imageDir needs to exist for backup to work
        if (!imageDir.exists()) imageDir.mkdirs()

        val footerPadding = Space(this)
        footerPadding.minimumHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            FOOTER_PADDING_DP,
            resources.displayMetrics
        ).toInt()
        binding.medListView.addFooterView(footerPadding)
        binding.medListView.setFooterDividersEnabled(false)
        mainScope.launch {
            launchMedicationsObserve()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        lifecycleScope.launch(lifecycleDispatcher) {

            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            sortType = sharedPref.getString(getString(R.string.sort_key), TIME_SORT)!!
            mainScope.launch {
                binding.medListView.onItemClickListener =
                    AdapterView.OnItemClickListener { adapterView, view, i, l ->
                        openMedDetailActivity(medications!![i].id, false)
                    }
            }

            if (BuildConfig.VERSION_CODE > sharedPref.getInt(getString(R.string.last_version_used_key), 0)) {
                ensureAlarmsScheduled()
            }
            with(sharedPref.edit()) {
                putInt(getString(R.string.last_version_used_key), BuildConfig.VERSION_CODE)
                apply()
            }

            val medId = intent.getLongExtra(getString(R.string.med_id_key), Medication.INVALID_MED_ID)
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
                    lifecycleScope.launch(lifecycleDispatcher) {
                        //Create alarm
                        scheduleMedicationAlarm(medication)
                    }
                }
            }
    }

    private fun scheduleMedicationAlarm(
        medication: Medication,
    ) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = AlarmIntentManager.buildNotificationAlarm(this@MainActivity, medication)
        alarmManager.cancel(alarmIntent)

        AlarmIntentManager.setExact(
            alarmManager,
            alarmIntent,
            medication.calculateNextDose().timeInMillis
        )
    }

    private fun openMedDetailActivity(medId: Long, takeMed: Boolean) {
        MedDetailActivity.start(this, medId, takeMed)
    }

    override fun onResume() {
        medicationListAdapter?.notifyDataSetChanged()
        binding.listEmptyLabel.visibility = if(medications.isNullOrEmpty()) View.VISIBLE else View.GONE

        lifecycleScope.launch(lifecycleDispatcher) {
            refreshJob = startRefresherLoop()
        }
        super.onResume()
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
        when (sortType) {
            TIME_SORT -> {
                item.icon = AppCompatResources.getDrawable(this, R.drawable.ic_sort_by_alpha)
                sortAndNotify(sortType, NAME_SORT)
            }
            NAME_SORT -> {
                item.icon = AppCompatResources.getDrawable(this, R.drawable.ic_sort_by_type)
                sortAndNotify(sortType, TYPE_SORT)
            }
            else -> {
                item.icon = AppCompatResources.getDrawable(this, R.drawable.ic_sort_by_time)
                sortAndNotify(sortType, TIME_SORT)
            }
        }
    }

    private fun sortAndNotify(sortType: String, nextSortType: String) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val comparator = getMedicationSortComparator(sortType)
        this.sortType = nextSortType
        with(sharedPref.edit()) {
            putString(getString(R.string.sort_key), sortType)
            apply()
        }
        medications?.sortWith(comparator)
        medicationListAdapter?.notifyDataSetChanged()
    }

    @Synchronized
    private fun refreshFromDatabase(localMedications: MutableList<Medication>) {
        val comparator = getMedicationSortComparator(sortType)
        medications = localMedications.sortedWith(comparator).toMutableList()
        medicationListAdapter = MedListAdapter(context, medications!!, medicationTypeDao(context).getAllRaw())
        mainScope.launch {
            binding.medListView.adapter = medicationListAdapter
            if (!medications.isNullOrEmpty())
                binding.listEmptyLabel.visibility = View.GONE
            else
                binding.listEmptyLabel.visibility = View.VISIBLE
        }
    }

    private fun getMedicationSortComparator(sortType: String): Comparator<Medication> {
        return when (this.sortType) {
            NAME_SORT -> {
                Comparator(Medication::compareByName)
            }
            TYPE_SORT -> {
                Comparator(Medication::compareByType)
            }
            else -> {
                Comparator(Medication::compareByTime)
            }
        }
    }

    private fun openAboutActivity() {
        lifecycleScope.launch (lifecycleDispatcher){
            stopRefresherLoop(refreshJob)
        }

        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun openAddMedActivity() {
        val intent = Intent(this, AddMedActivity::class.java)
        startActivity(intent)
    }

    private fun restoreDatabase() {
        lifecycleScope.launch (lifecycleDispatcher){
            val restoreUri = ActivityResultManager.getInstance().getContent(MIME_TYPES) ?: return@launch
            stopRefresherLoop(refreshJob)
            restoreJob = lifecycleScope.launch(lifecycleDispatcher) {
                doRestore(this@MainActivity, restoreUri, tempDir, imageDir)
            }
        }
    }


    private fun backUpDatabase() {
        // Intent.normalizeMimeType(MIME_TYPES), intent.addCategory(Intent.CATEGORY_OPENABLE)
        lifecycleScope.launch (lifecycleDispatcher){
            val suggestedName = MedicationDB.DATABASE_NAME + ZipFileManager.ZIP_FILE_EXTENSION
            val backupUri = ActivityResultManager.getInstance().createDocument(MIME_TYPES, suggestedName) ?: return@launch
            doBackup(context, backupUri, tempDir, imageDir)
        }
    }

    override fun onPause() {
        runBlocking {
           stopRefresherLoop(refreshJob)
        }
        super.onPause()
    }

    private fun startRefresherLoop(): Job {
        return lifecycleScope.launch(lifecycleDispatcher) {

            runCatching {
                restoreJob?.join()
                backupJob?.join()
            }.onFailure { throwable ->
                throwable.printStackTrace()
            }
            while (medicationDao(context).getAllRaw().isNotEmpty()) {
                val medication = medicationDao(context).getAllRaw()
                    .sortedWith(Medication::compareByClosestDoseTransition).first()

                val transitionDelay = medication.closestDoseTransitionTime() - System.currentTimeMillis()

                val delayDuration =
                    when {
                        transitionDelay < MINIMUM_DELAY -> {
                            MINIMUM_DELAY
                        }
                        transitionDelay in MINIMUM_DELAY until MAXIMUM_DELAY -> {
                            transitionDelay
                        }
                        else -> {
                            MAXIMUM_DELAY
                        }
                    }

                delay(delayDuration)
                refreshFromDatabase(medicationDao(context).getAllRaw())
            }
        }
    }

    private suspend fun stopRefresherLoop(refresher: Job?) {
        runCatching {
            refresher?.cancelAndJoin()
        }.onFailure { throwable ->
            throwable.printStackTrace()
        }
    }


    private suspend fun doRestore(context: Context, restoreUri: Uri, tempFolder: File, imageFolder: File) {
        if (MedicationDB.databaseFileIsValid(applicationContext, restoreUri)) {
            doRestoreDb(restoreUri)
        } else {
            doCleanupDb(restoreUri, tempFolder, imageFolder, context)
        }
    }

    private suspend fun doBackup(context: Context, backupUri: Uri, tempFolder: File, imageFolder: File) {
        stopRefresherLoop(refreshJob)
        runCatching {
            MedicationDB.getInstance(context).close()
            MedicationDB.wipeInstance()

            if (!tempFolder.exists()) tempFolder.mkdirs()
            if (!imageFolder.exists()) imageFolder.mkdirs()

            tempFolder.listFiles()?.iterator()?.forEach { entry ->
                entry.deleteRecursively()
            }

            val databaseFile = getDatabasePath(MedicationDB.DATABASE_NAME)
            databaseFile.copyTo(File(tempFolder.path + File.separator + databaseFile.name))

            imageFolder.copyRecursively(File(tempFolder.path + File.separator + imageFolder.name), true)

            contentResolver.openOutputStream(backupUri)?.use { outputStream ->
                ZipFileManager.streamFolderToZip(tempFolder, outputStream)
            }

            tempFolder.deleteRecursively()
        }.onFailure { exception ->
            exception.printStackTrace()
            ensureRefresherLoop()
            mainScope.launch {
                Toast.makeText(context, getString(R.string.back_up_failed), Toast.LENGTH_SHORT).show()
            }
        }.onSuccess {
            ensureRefresherLoop()
            mainScope.launch {
                Toast.makeText(context, getString(R.string.back_up_successful), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun doRestoreDb(restoreUri: Uri) {
        stopRefresherLoop(refreshJob)
        MedicationDB.getInstance(applicationContext).close()
        MedicationDB.wipeInstance()

        withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(restoreUri)?.use { inputStream ->
                    applicationContext.getDatabasePath(MedicationDB.DATABASE_NAME).outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }.onSuccess {
                ensureRefresherLoop()
                mainScope.launch {
                    Toast.makeText(applicationContext, getString(R.string.database_restored), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { exception ->
                exception.printStackTrace()
                ensureRefresherLoop()
                mainScope.launch {
                    Toast.makeText(applicationContext, getString(R.string.database_is_invalid), Toast.LENGTH_SHORT).show()
                }
            }
        }

        mainScope.launch {
           launchMedicationsObserve()
        }
    }

    private suspend fun doCleanupDb(
        restoreUri: Uri,
        tempFolder: File,
        imageFolder: File,
        context: Context
    ) {
        withContext(Dispatchers.IO) {
            tempFolder.deleteRecursively()
            runCatching {
                contentResolver.openInputStream(restoreUri)?.use { inputStream ->
                    ZipFileManager.streamZipToFolder(
                        inputStream,
                        tempFolder
                    )
                }

                val tempFiles = tempFolder.listFiles()

                val databaseFile =
                    tempFiles?.find { file -> file.name == MedicationDB.DATABASE_NAME }

                databaseFile?.inputStream()?.use { databaseStream ->
                    if (MedicationDB.databaseFileIsValid(context, databaseFile.toUri())) {
                        stopRefresherLoop(refreshJob)
                        MedicationDB.getInstance(applicationContext).close()
                        MedicationDB.wipeInstance()
                        context.getDatabasePath(MedicationDB.DATABASE_NAME).outputStream()
                            .use { outStream ->
                                databaseStream.copyTo(outStream)
                            }
                    }
                }

                imageFolder.listFiles()?.forEach { file ->
                    if (file.exists()) file.deleteRecursively()
                }

                val tempImageFolder =
                    tempFiles?.find { file -> file.isDirectory && file.name == imageFolder.name }

                tempImageFolder?.copyRecursively(imageFolder)

                if (tempFolder.exists()) {
                    tempFolder.deleteRecursively()
                }
            }.onFailure { exception ->
                exception.printStackTrace()
                ensureRefresherLoop()
                mainScope.launch {
                    Toast.makeText(applicationContext, getString(R.string.database_is_invalid), Toast.LENGTH_SHORT).show()
                }
            }.onSuccess {
                ensureRefresherLoop()
                mainScope.launch {
                    launchMedicationsObserve {
                        Toast.makeText(applicationContext, getString(R.string.database_restored), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun launchMedicationsObserve(additionalAction: (() -> Unit)? = null) {
        medicationDao(applicationContext).getAll()
            .observe(this@MainActivity) { medicationList ->
                lifecycleScope.launch(lifecycleDispatcher) {
                    refreshFromDatabase(medicationList)
                }
            }

        additionalAction?.invoke()

    }
    private fun ensureRefresherLoop() {
        if (refreshJob?.isActive != true) {
            refreshJob = startRefresherLoop()
        }
    }
}