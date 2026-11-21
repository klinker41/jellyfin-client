package com.makd.afinity.util

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinImageUrlBuilderTest {

    private val builder = JellyfinImageUrlBuilder()

    @Test
    fun buildOptimizedImageUrlTrimsTrailingSlashesAndBuildsQueryParams() {
        val url =
            builder.buildOptimizedImageUrl(
                baseUrl = "https://media.example.com///",
                itemId = "item-1",
                imageType = "Thumb",
                tag = "t123",
                maxWidth = 640,
                maxHeight = 360,
                quality = 75,
                format = "jpg",
                endpoint = "Collections",
            )
        assertEquals(
            "https://media.example.com/Collections/item-1/Images/Thumb?tag=t123&maxWidth=640&maxHeight=360&quality=75&format=jpg",
            url,
        )
    }

    @Test
    fun convenienceMethodsUseExpectedEndpointsImageTypesAndDefaults() {
        assertEquals(
            "https://jf.local/Items/abc/Images/Primary?quality=90&format=webp",
            builder.buildPrimaryImageUrl(baseUrl = "https://jf.local/", itemId = "abc"),
        )
        assertEquals(
            "https://jf.local/Users/u1/Images/Primary?tag=avatar&maxWidth=200&quality=90&format=webp",
            builder.buildUserPrimaryImageUrl(
                baseUrl = "https://jf.local",
                userId = "u1",
                tag = "avatar",
                maxWidth = 200,
            ),
        )
        assertEquals(
            "https://jf.local/Items/m1/Images/Backdrop?maxWidth=1920&quality=85&format=webp",
            builder.buildBackdropUrl(baseUrl = "https://jf.local", itemId = "m1"),
        )
    }

    @Test
    fun dpToPxApplies120PercentOversampleScale() {
        val density = Density(density = 2.5f)
        // 100dp * 2.5 = 250px; * 1.2 = 300px
        assertEquals(300, builder.dpToPx(100.dp, density))
    }
}
