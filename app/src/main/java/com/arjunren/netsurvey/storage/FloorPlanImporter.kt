package com.arjunren.netsurvey.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.InstallationPhotoEntity
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FloorPlanImporter(private val context: Context) {
    suspend fun import(floorId: Long, uri: Uri): FloorPlanEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val directory = File(context.filesDir, "floorplans").apply { mkdirs() }
        val temporary = File.createTempFile("floor-$floorId-", ".import", directory)
        try {
            val input = resolver.openInputStream(uri)
                ?: error("Unable to open the selected floor plan. Try choosing it from the Files app.")
            input.buffered().use { source ->
                temporary.outputStream().buffered().use { output ->
                    copyWithLimit(source, output, MAX_IMAGE_BYTES, "Floor plan is larger than 25 MB.")
                }
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, options)
            require(options.outWidth in 1..MAX_DIMENSION && options.outHeight in 1..MAX_DIMENSION) {
                "Floor plan dimensions are unsupported. Maximum is ${MAX_DIMENSION}px per side."
            }
            val detectedMime = detectSupportedImageMime(temporary)
                ?: error("The selected content is not a valid PNG or JPEG image.")

            val extension = if (detectedMime == "image/png") "png" else "jpg"
            val target = File(directory, "floor-$floorId-${UUID.randomUUID()}.$extension")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target)
                temporary.delete()
            }
            FloorPlanEntity(
                floorId = floorId,
                localPath = target.absolutePath,
                mimeType = detectedMime,
                width = options.outWidth,
                height = options.outHeight,
            )
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        private const val MAX_DIMENSION = 12_000
    }
}

class InstallationPhotoImporter(private val context: Context) {
    suspend fun import(projectId: Long, floorId: Long?, uri: Uri): InstallationPhotoEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val directory = File(context.filesDir, "installation-photos").apply { mkdirs() }
        val temporary = File.createTempFile("project-$projectId-", ".import", directory)
        try {
            val input = resolver.openInputStream(uri)
                ?: error("Unable to open the selected photo. Try choosing it from the Files app.")
            input.buffered().use { source ->
                temporary.outputStream().buffered().use { output ->
                    copyWithLimit(source, output, MAX_IMPORT_BYTES, "Photo is larger than 25 MB.")
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, options)
            val detectedMime = detectSupportedImageMime(temporary)
            require(detectedMime != null && options.outWidth in 1..MAX_IMPORT_DIMENSION && options.outHeight in 1..MAX_IMPORT_DIMENSION) {
                "Choose a valid PNG or JPEG installation photo."
            }
            val extension = if (detectedMime == "image/png") "png" else "jpg"
            val target = File(directory, "project-$projectId-${UUID.randomUUID()}.$extension")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target)
                temporary.delete()
            }
            InstallationPhotoEntity(projectId = projectId, floorId = floorId, localPath = target.absolutePath)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }
}

private const val MAX_IMPORT_BYTES = 25L * 1024 * 1024
private const val MAX_IMPORT_DIMENSION = 12_000

private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long, message: String): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= limit) { message }
        output.write(buffer, 0, read)
    }
    return total
}

private fun detectSupportedImageMime(file: File): String? {
    val signature = ByteArray(8)
    val count = file.inputStream().buffered().use { it.read(signature) }
    if (count >= 8 && signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
        return "image/png"
    }
    if (count >= 3 && signature[0] == 0xFF.toByte() && signature[1] == 0xD8.toByte() && signature[2] == 0xFF.toByte()) {
        return "image/jpeg"
    }
    return null
}
