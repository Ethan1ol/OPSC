package com.example.opsc.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Turns a photo the user picked (gallery or camera) into a small JPEG ready to store: it decodes
 * a reduced version (so big photos do not use all the memory), fixes the rotation from the EXIF
 * data, crops to the wanted shape from the centre, shrinks it, and lowers the quality until the
 * file is small enough. The arithmetic lives in [ImageMath] and is unit tested.
 */
object ImageProcessor {
    private const val TAG = "ImageProcessor"

    /** Returns the JPEG bytes, or null if the picture could not be read. */
    suspend fun process(context: Context, uri: Uri, spec: ImageSpec): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val resolver = context.contentResolver

            // 1. Read only the size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // 2. Decode a reduced copy.
            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight, spec.maxWidth, spec.maxHeight)
            }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@withContext null

            // 3. Straighten it using the camera's rotation information.
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            bitmap = rotate(bitmap, orientation)

            // 4. Crop to the shape, then shrink.
            val aspectW = spec.aspectWidth
            val aspectH = spec.aspectHeight
            if (aspectW != null && aspectH != null) {
                val crop = ImageMath.centerCrop(bitmap.width, bitmap.height, aspectW, aspectH)
                bitmap = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
            }
            val (width, height) = ImageMath.scaledSize(bitmap.width, bitmap.height, spec.maxWidth, spec.maxHeight)
            if (width != bitmap.width || height != bitmap.height) bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

            // 5. Compress, lowering the quality until it fits.
            var quality: Int? = ImageMath.QUALITY_STEPS.first()
            while (quality != null) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= ImageMath.MAX_BYTES) {
                    Log.i(TAG, "Processed image: ${width}x$height, ${bytes.size} bytes at quality $quality")
                    return@withContext bytes
                }
                quality = ImageMath.nextQuality(quality)
            }
            Log.w(TAG, "Image still too large at the lowest quality")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not process image", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while processing image", e)
            null
        }
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
