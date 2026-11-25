package com.eric.toolbox.modules.read

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore

/** Limit decoded size of large still images before handing them to the WebView renderer. */
internal object ReaderImages {
    private val decoders = Semaphore(2)

    fun open(book: BookContent, path: String): BookContent.AssetStream? {
        val asset = book.openAsset(path) ?: return null
        if (asset.mime != "image/png" && asset.mime != "image/jpeg") return asset

        // Only large PNG/JPEG images need a display copy. Keep original streams for small images.
        return try {
            asset.stream.use { stream ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                val sample = sampleSize(bounds.outWidth, bounds.outHeight)
                if (sample == 1) return@use book.openAsset(path)
                decoders.acquire()
                try {
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap = book.openAsset(path)?.stream?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: return@use null
                    try {
                        val output = ByteArrayOutputStream()
                        val alpha = bitmap.hasAlpha()
                        if (!bitmap.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, output)) {
                            return@use null
                        }
                        BookContent.AssetStream(
                            ByteArrayInputStream(output.toByteArray()),
                            if (alpha) "image/png" else "image/jpeg",
                        )
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    decoders.release()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > 1800 ||
            (width.toLong() / sample) * (height.toLong() / sample) > 1_800_000L
        ) {
            sample *= 2
        }
        return sample
    }
}
