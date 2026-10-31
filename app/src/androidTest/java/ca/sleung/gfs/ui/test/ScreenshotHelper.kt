package ca.sleung.gfs.ui.test

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

object ScreenshotHelper {

    fun captureAndSave(node: SemanticsNodeInteraction, filename: String): File {
        val bitmap = node.captureToImage().asAndroidBitmap()
        check(bitmap.width > 0 && bitmap.height > 0) { "Captured screenshot has invalid dimensions" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotsDir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(screenshotsDir, "$filename.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
