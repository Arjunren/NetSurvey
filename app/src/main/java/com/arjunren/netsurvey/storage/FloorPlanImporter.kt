package com.arjunren.netsurvey.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.arjunren.netsurvey.data.local.FloorPlanEntity
import com.arjunren.netsurvey.data.local.InstallationPhotoEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FloorPlanImporter(private val context: Context) {
    suspend fun import(floorId: Long, uri: Uri): FloorPlanEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val declaredType = resolver.getType(uri).orEmpty()
        require(declaredType in setOf("image/png", "image/jpeg", "image/jpg")) {
            "Choose a PNG or JPEG floor plan."
        }
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            require(descriptor.length < 0 || descriptor.length <= MAX_IMAGE_BYTES) { "Floor plan is larger than 25 MB." }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.buffered()?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unable to read the selected floor plan.")
        require(options.outWidth in 1..MAX_DIMENSION && options.outHeight in 1..MAX_DIMENSION) {
            "Floor plan dimensions are unsupported. Maximum is ${MAX_DIMENSION}px per side."
        }
        val detectedMime = options.outMimeType.orEmpty()
        require(detectedMime in setOf("image/png", "image/jpeg")) { "The selected content is not a valid PNG or JPEG image." }
        val directory = File(context.filesDir, "floorplans").apply { mkdirs() }
        val extension = if (detectedMime == "image/png") "png" else "jpg"
        val target = File(directory, "floor-$floorId-${UUID.randomUUID()}.$extension")
        resolver.openInputStream(uri)?.buffered()?.use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
        } ?: error("Unable to copy the selected floor plan.")
        FloorPlanEntity(
            floorId = floorId,
            localPath = target.absolutePath,
            mimeType = detectedMime,
            width = options.outWidth,
            height = options.outHeight,
        )
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 25L * 1024 * 1024
        private const val MAX_DIMENSION = 12_000
    }
}

class InstallationPhotoImporter(private val context: Context) {
    suspend fun import(projectId: Long, floorId: Long?, uri: Uri): InstallationPhotoEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openAssetFileDescriptor(uri, "r")?.use { require(it.length < 0 || it.length <= 25L * 1024 * 1024) { "Photo is larger than 25 MB." } }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Unable to read photo.")
        require(options.outMimeType in setOf("image/png", "image/jpeg") && options.outWidth in 1..12_000 && options.outHeight in 1..12_000) { "Choose a valid PNG or JPEG installation photo." }
        val extension = if (options.outMimeType == "image/png") "png" else "jpg"
        val directory = File(context.filesDir, "installation-photos").apply { mkdirs() }
        val target = File(directory, "project-$projectId-${UUID.randomUUID()}.$extension")
        resolver.openInputStream(uri)?.buffered()?.use { input -> target.outputStream().buffered().use(input::copyTo) } ?: error("Unable to copy photo.")
        InstallationPhotoEntity(projectId = projectId, floorId = floorId, localPath = target.absolutePath)
    }
}
