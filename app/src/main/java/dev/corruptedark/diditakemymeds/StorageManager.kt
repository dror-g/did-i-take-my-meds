package dev.corruptedark.diditakemymeds

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dev.corruptedark.diditakemymeds.data.models.ProofImage
import java.io.File
import java.io.IOException
import java.net.URI

object StorageManager {
    val IMAGE_PATH = "images"
    val IMAGE_FILE_EXT = "jpg"
    @Throws(IOException::class, SecurityException::class)
    fun createImageFile(context: Context, medId: Long, doseTime: Long): File {
        val folder = File(context.filesDir.path, IMAGE_PATH).apply { mkdirs() }
        val fileName = "${medId}_$doseTime"
        return File.createTempFile(fileName, IMAGE_FILE_EXT, folder)
    }

    @Throws(IOException::class, SecurityException::class)
    fun deleteImageFile(context:Context, proofImage: ProofImage) {
        val folder = File(context.filesDir.path, IMAGE_PATH)
        val file = File(folder, proofImage.filePath)
        if (file.exists() && file.canWrite()) {
            file.delete()
        }
    }

    fun getImageUri(context: Context, proofImage: ProofImage): Uri? {
        val folder = File(context.filesDir.path, IMAGE_PATH)
        val file = File(folder, proofImage.filePath)
        if (!file.exists()) return null
        if (!file.canRead()) return null
        return file.toUri()
    }
}