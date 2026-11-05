package com.example.opsc.media

/** A rectangle inside an image, in pixels. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** What an image will be used for: its target size, shape and how large the stored file may be. */
enum class ImageSpec(val maxWidth: Int, val maxHeight: Int, val aspectWidth: Int?, val aspectHeight: Int?) {
    /** A square profile picture. */
    AVATAR(512, 512, 1, 1),
    /** A wide banner, 8:3. */
    BANNER(1280, 480, 8, 3),
    /** A proof photo: kept in its own shape, only shrunk. */
    PROOF(1280, 1280, null, null)
}

/**
 * The arithmetic for cropping, shrinking and compressing images, without any Android code so it
 * can be unit tested. The images are stored as blobs in Firestore, whose documents are limited to
 * 1 MiB, so every image is cropped to its shape, scaled down and compressed until it is small.
 */
object ImageMath {
    /** Largest stored image: well under the 1 MiB Firestore document limit. */
    const val MAX_BYTES = 700_000

    /** JPEG qualities to try, best first, until the file is small enough. */
    val QUALITY_STEPS = listOf(85, 75, 65, 55, 45, 35, 25)

    /** The largest centred rectangle with the given aspect ratio that fits inside the image. */
    fun centerCrop(srcWidth: Int, srcHeight: Int, aspectWidth: Int, aspectHeight: Int): CropRect {
        require(srcWidth > 0 && srcHeight > 0 && aspectWidth > 0 && aspectHeight > 0)
        // Compare srcWidth/srcHeight with aspectWidth/aspectHeight using integers.
        val srcIsWider = srcWidth.toLong() * aspectHeight > srcHeight.toLong() * aspectWidth
        return if (srcIsWider) {
            val width = (srcHeight.toLong() * aspectWidth / aspectHeight).toInt().coerceIn(1, srcWidth)
            CropRect((srcWidth - width) / 2, 0, width, srcHeight)
        } else {
            val height = (srcWidth.toLong() * aspectHeight / aspectWidth).toInt().coerceIn(1, srcHeight)
            CropRect(0, (srcHeight - height) / 2, srcWidth, height)
        }
    }

    /** The size after shrinking to fit inside a box. Images are never enlarged. */
    fun scaledSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        if (width <= maxWidth && height <= maxHeight) return width to height
        val scale = minOf(maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /**
     * A power-of-two "sample size" for decoding: the biggest that still leaves the decoded image at
     * least as large as the target, so a 12 MP photo is not fully loaded into memory.
     */
    fun sampleSize(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= targetWidth && srcHeight / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    /** The next, lower quality to try, or null when there is nothing lower. */
    fun nextQuality(current: Int): Int? = QUALITY_STEPS.firstOrNull { it < current }
}
