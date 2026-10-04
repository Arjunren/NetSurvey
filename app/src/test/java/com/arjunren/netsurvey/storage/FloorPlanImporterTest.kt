package com.arjunren.netsurvey.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloorPlanImporterTest {
    @Test
    fun validPngIsCopiedAndItsBoundsAreRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "floor-plan-source.png")
        val bitmap = Bitmap.createBitmap(7, 5, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        val imported = FloorPlanImporter(context).import(42, Uri.fromFile(source))

        assertEquals(7, imported.width)
        assertEquals(5, imported.height)
        assertEquals("image/png", imported.mimeType)
        assertTrue(File(imported.localPath).isFile)
    }
}
