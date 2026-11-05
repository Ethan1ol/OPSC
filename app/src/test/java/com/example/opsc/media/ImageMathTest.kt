package com.example.opsc.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the image crop, scale and compression arithmetic. */
class ImageMathTest {

    @Test fun squareCropOfALandscapePhotoKeepsTheCentre() {
        val crop = ImageMath.centerCrop(4000, 3000, 1, 1)
        assertEquals(CropRect(500, 0, 3000, 3000), crop)
    }

    @Test fun squareCropOfAPortraitPhotoKeepsTheCentre() {
        val crop = ImageMath.centerCrop(3000, 4000, 1, 1)
        assertEquals(CropRect(0, 500, 3000, 3000), crop)
    }

    @Test fun alreadySquareIsUnchanged() {
        assertEquals(CropRect(0, 0, 800, 800), ImageMath.centerCrop(800, 800, 1, 1))
    }

    @Test fun bannerCropIsWide() {
        val crop = ImageMath.centerCrop(4000, 3000, 8, 3)
        assertEquals(4000, crop.width)
        assertEquals(1500, crop.height)
        assertEquals(750, crop.top)
        assertEquals(0, crop.left)
    }

    @Test fun cropNeverLeavesTheImage() {
        for ((w, h) in listOf(1 to 1, 10 to 3, 3 to 10, 4032 to 3024, 3024 to 4032, 100 to 7)) {
            for ((aw, ah) in listOf(1 to 1, 8 to 3, 3 to 8)) {
                val c = ImageMath.centerCrop(w, h, aw, ah)
                assertTrue(c.left >= 0 && c.top >= 0 && c.width >= 1 && c.height >= 1)
                assertTrue(c.left + c.width <= w && c.top + c.height <= h)
            }
        }
    }

    @Test fun smallImagesAreNeverEnlarged() {
        assertEquals(200 to 100, ImageMath.scaledSize(200, 100, 512, 512))
    }

    @Test fun bigImagesShrinkKeepingTheirShape() {
        assertEquals(512 to 512, ImageMath.scaledSize(3000, 3000, 512, 512))
        assertEquals(1280 to 480, ImageMath.scaledSize(4000, 1500, 1280, 480))
        assertEquals(1280 to 960, ImageMath.scaledSize(4000, 3000, 1280, 1280))
    }

    @Test fun sampleSizeIsAPowerOfTwoThatKeepsEnoughPixels() {
        assertEquals(1, ImageMath.sampleSize(600, 600, 512, 512))
        assertEquals(4, ImageMath.sampleSize(4000, 3000, 512, 512))
        val s = ImageMath.sampleSize(4000, 3000, 512, 512)
        assertTrue(4000 / s >= 512 && 3000 / s >= 512)
    }

    @Test fun qualityStepsGoDownAndEnd() {
        assertEquals(75, ImageMath.nextQuality(85))
        assertEquals(25, ImageMath.nextQuality(35))
        assertNull(ImageMath.nextQuality(25))
    }

    @Test fun maxStoredSizeIsBelowTheFirestoreDocumentLimit() {
        assertTrue(ImageMath.MAX_BYTES < 1_048_576)
    }

    @Test fun specsHaveTheRightShapes() {
        assertEquals(1, ImageSpec.AVATAR.aspectWidth)
        assertEquals(8, ImageSpec.BANNER.aspectWidth)
        assertNull(ImageSpec.PROOF.aspectWidth)
    }
}
