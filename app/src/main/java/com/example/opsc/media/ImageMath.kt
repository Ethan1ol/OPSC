package com.example.opsc.media

/** A rectangle inside an image, in pixels. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** What an image is used for: its size, shape and file limit. */
enum class ImageSpec(val maxWidth: Int, val maxHeight: Int, val aspectWidth: Int?, val aspectHeight: Int?) {
    /** A square profile picture. */
    AVATAR(512, 512, 1, 1),
    /** A wide banner, 8:3. */
    BANNER(1280, 480, 8, 3),
    /** A proof photo: kept in its own shape, only shrunk. */
    PROOF(1280, 1280, null, null)
}

/** The maths for cropping, shrinking and compressing images, with no Android code so it can be unit tested. Images are stored in Firestore (1 MiB limit), so each one is cropped, scaled down and compressed. */
object ImageMath {
    /** Largest stored image, well under the 1 MiB limit. */
    const val MAX_BYTES = 700_000

    /** JPEG qualities to try, best first. */
    val QUALITY_STEPS = listOf(85, 75, 65, 55, 45, 35, 25)

    /** The biggest centred rectangle with this shape that fits in the image. */
    fun centerCrop(srcWidth: Int, srcHeight: Int, aspectWidth: Int, aspectHeight: Int): CropRect {
        require(srcWidth > 0 && srcHeight > 0 && aspectWidth > 0 && aspectHeight > 0)
        // Compare the ratios using whole numbers.
        val srcIsWider = srcWidth.toLong() * aspectHeight > srcHeight.toLong() * aspectWidth
        return if (srcIsWider) {
            val width = (srcHeight.toLong() * aspectWidth / aspectHeight).toInt().coerceIn(1, srcWidth)
            CropRect((srcWidth - width) / 2, 0, width, srcHeight)
        } else {
            val height = (srcWidth.toLong() * aspectHeight / aspectWidth).toInt().coerceIn(1, srcHeight)
            CropRect(0, (srcHeight - height) / 2, srcWidth, height)
        }
    }

    /** Size after shrinking to fit a box. Images are never enlarged. */
    fun scaledSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        if (width <= maxWidth && height <= maxHeight) return width to height
        val scale = minOf(maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** A power-of-two decode size, so a large photo does not fill the memory. */
    fun sampleSize(srcWidth: Int, srcHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= targetWidth && srcHeight / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    /** The next lower quality, or null if there is none. */
    fun nextQuality(current: Int): Int? = QUALITY_STEPS.firstOrNull { it < current }
}
