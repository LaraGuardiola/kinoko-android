package dev.kinoko.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentsTest {
    @Test
    fun photosShrinkByPowersOfTwoToTheLongSide() {
        assertEquals(1, sampleSize(1600, 1200, PHOTO_MAX)) // small enough already
        assertEquals(2, sampleSize(4000, 3000, PHOTO_MAX)) // 2000 on its long side
        assertEquals(4, sampleSize(3000, 8192, PHOTO_MAX)) // a tall one: 2048
    }

    @Test
    fun aReEncodedPhotoIsAJpegOfTheSameName() {
        assertEquals("IMG_2041.jpg", jpegName("IMG_2041.HEIC"))
        assertEquals("shot.jpg", jpegName("shot"))
        assertEquals("photo.jpg", jpegName(".png"))
    }

    @Test
    fun sizesReadLikeAPerson() {
        assertEquals("512 B", sizeText(512))
        assertEquals("200 KB", sizeText(200 * 1024))
        assertEquals("3.5 MB", sizeText(3_670_016))
    }
}
