package com.sih.app.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

private const val TAG = "AgriX_ImageUtils"
private const val MAX_IMAGE_DIMENSION = 1024

object ImageUtils {

    fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var sampleSize = 1
            while (options.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
                options.outHeight / sampleSize > MAX_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load and downsample image from URI: ${e.message}", e)
            null
        }
    }
}
