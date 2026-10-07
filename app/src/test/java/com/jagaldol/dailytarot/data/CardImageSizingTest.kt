package com.jagaldol.dailytarot.data

import org.junit.Assert.assertTrue
import org.junit.Test

class CardImageSizingTest {
    @Test
    fun widgetImageFitsBothSmallAndLargeDisplays() {
        for ((width, height) in listOf(320 to 480, 480 to 800, 1080 to 2400, 2400 to 1080)) {
            val imageHeight = widgetImageHeight(width, height)
            val imageWidth = imageHeight * 3 / 5
            assertTrue(imageHeight <= 1000)
            assertTrue(imageWidth <= width)
            assertTrue(imageHeight <= height)
            assertTrue(imageWidth * imageHeight * 4 <= width * height * 4)
        }
    }

}
