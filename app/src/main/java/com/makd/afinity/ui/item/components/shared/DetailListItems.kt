package com.makd.afinity.ui.item.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.ui.components.DetailEdgePadding
import com.makd.afinity.ui.components.LocalDetailEdgePadding
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.components.afinitySectionTitleStyle
import com.makd.afinity.ui.utils.gapIfNotEmpty

val DetailSectionGap = 16.dp

val LocalDetailLayout = staticCompositionLocalOf { DetailLayout.CLASSIC }

fun LazyListScope.detailItem(
    key: Any,
    horizontalPadding: Dp,
    gap: Dp = DetailSectionGap,
    contentType: Any? = null,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        DetailItemBox(horizontalPadding = horizontalPadding, gap = gap, content = content)
    }
}

fun LazyListScope.detailRowItem(
    key: Any,
    gap: Dp = DetailSectionGap,
    contentType: Any? = null,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(modifier = Modifier.fillMaxWidth().gapIfNotEmpty(gap)) { content() }
    }
}

@Composable
fun DetailItemBox(horizontalPadding: Dp, gap: Dp, content: @Composable () -> Unit) {
    val edgePadding = LocalDetailEdgePadding.current
    val startPadding = if (edgePadding.start > 0.dp) edgePadding.start else horizontalPadding
    val endPadding = if (edgePadding.end > 0.dp) edgePadding.end else horizontalPadding
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .gapIfNotEmpty(gap)
                .padding(start = startPadding, end = endPadding),
    ) {
        CompositionLocalProvider(
            LocalDetailEdgePadding provides DetailEdgePadding.Zero,
            LocalPinnedDrawerStartInset provides 0.dp,
        ) {
            content()
        }
    }
}

@Composable
fun DetailSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = afinitySectionTitleStyle(),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
