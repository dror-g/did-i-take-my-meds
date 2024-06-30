package dev.corruptedark.diditakemymeds

import android.content.Context
import dev.corruptedark.diditakemymeds.data.models.ProofImage
import java.io.File
import java.io.IOException

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
}