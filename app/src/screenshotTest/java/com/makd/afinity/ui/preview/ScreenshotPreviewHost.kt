package com.makd.afinity.ui.preview

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.asPainter
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.ui.theme.ThemeMode
import kotlin.math.absoluteValue

private fun screenshotPreviewImage(data: Any?): Image {
    val hue = ((data?.toString().orEmpty()).hashCode().absoluteValue % 360).toFloat()
    val top = Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.8f))
    val bottom = Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.65f, 0.3f))
    return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        .asImage()
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun ScreenshotPreviewHost(
    modifier: Modifier = Modifier,
    cardSize: CardSize = CardSize.DEFAULT,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    playerOffset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val imageHandler = remember {
        object : AsyncImagePreviewHandler {
            override suspend fun handle(
                imageLoader: ImageLoader,
                request: ImageRequest,
            ): AsyncImagePainter.State {
                val image = screenshotPreviewImage(request.data)
                return AsyncImagePainter.State.Success(
                    painter = image.asPainter(request.context),
                    result =
                        SuccessResult(
                            image = image,
                            request = request,
                            dataSource = DataSource.MEMORY_CACHE,
                        ),
                )
            }
        }
    }
    PreviewHost(
        modifier = modifier,
        cardSize = cardSize,
        themeMode = themeMode,
        playerOffset = playerOffset,
    ) {
        CompositionLocalProvider(LocalAsyncImagePreviewHandler provides imageHandler) {
            content()
        }
    }
}
