package dev.corruptedark.diditakemymeds

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.core.net.toUri
import dev.corruptedark.diditakemymeds.data.db.MedicationDB
import dev.corruptedark.diditakemymeds.data.models.ProofImage
import dev.corruptedark.diditakemymeds.util.ZipFileManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Date

object StorageManager {
    val IMAGE_PATH = "images"
    val TEMP_DIR_PATH = "tmp"
    val IMAGE_FILE_EXT = "jpg"

    @Throws(IOException::class, SecurityException::class)
    fun createImageFile(context: Context, medId: Long, doseTime: Long): File {
        val folder = getImagesFolder(context)
        val fileName = "${medId}_$doseTime"
        return File.createTempFile(fileName, IMAGE_FILE_EXT, folder)
    }

    @Throws(IOException::class, SecurityException::class)
    fun deleteImageFile(context: Context, proofImage: ProofImage) {
        val folder = getImagesFolder(context)
        val file = File(folder, proofImage.filePath)
        if (file.exists() && file.canWrite()) {
            file.delete()
        }
    }

    fun getImageUri(context: Context, proofImage: ProofImage): Uri? {
        val folder = getImagesFolder(context)
        val file = File(folder, proofImage.filePath)
        if (!file.exists()) return null
        if (!file.canRead()) return null
        return file.toUri()
    }

    fun suggestBackupFileName(): String {
        val date = Date()
        val formattedDate = DateFormat.format("yyyy-MM-dd-HH-mm", date)
        return "${MedicationDB.DATABASE_NAME}-$formattedDate${ZipFileManager.ZIP_FILE_EXTENSION}"
    }


    private fun getImagesFolder(context: Context): File {
        return File(context.filesDir.path, IMAGE_PATH).apply { mkdirs() }
    }


    private fun getTempFolder(context: Context): File {
        return File(context.filesDir.path, TEMP_DIR_PATH).apply { mkdirs() }
    }

    @Throws(IOException::class, SecurityException::class)
    fun restore(context: Context, restoreUri: Uri) {
        MedicationDB.getInstance(context).close()
        MedicationDB.wipeInstance()
        context.contentResolver.openInputStream(restoreUri)?.use { inputStream ->
            context.getDatabasePath(MedicationDB.DATABASE_NAME).outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    @Throws(IOException::class, SecurityException::class)
    fun backup(context: Context, backupUri: Uri) {
        MedicationDB.getInstance(context).close()
        MedicationDB.wipeInstance()
        val tempFolder = getTempFolder(context)
        val imageFolder = getImagesFolder(context)


        tempFolder.listFiles()?.iterator()?.forEach { entry ->
            entry.deleteRecursively()
        }

        val databaseFile = context.getDatabasePath(MedicationDB.DATABASE_NAME)
        databaseFile.copyTo(File(tempFolder.path + File.separator + databaseFile.name))

        imageFolder.copyRecursively(File(tempFolder.path + File.separator + imageFolder.name), true)

        context.contentResolver.openOutputStream(backupUri)?.use { outputStream ->
            ZipFileManager.streamFolderToZip(tempFolder, outputStream)
        }

        tempFolder.deleteRecursively()
    }

    @Throws(IOException::class, SecurityException::class)
    fun tryRestoreDb(context: Context, restoreUri: Uri) {
        MedicationDB.getInstance(context).close()
        MedicationDB.wipeInstance()
        val tempFolder = getTempFolder(context)
        val imageFolder = getImagesFolder(context)
        tempFolder.deleteRecursively()

        context.contentResolver.openInputStream(restoreUri)?.use { inputStream ->
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
    }
}