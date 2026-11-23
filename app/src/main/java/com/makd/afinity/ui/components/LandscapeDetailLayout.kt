package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.item.components.shared.LandscapeDetailTheme

sealed interface LandscapeDetailArrangement {
    data object Single : LandscapeDetailArrangement

    data object Split : LandscapeDetailArrangement

    data class Sidebar(val width: Dp) : LandscapeDetailArrangement
}

@Composable
fun LandscapeDetailLayout(
    backdropStyle: BackdropStyle,
    backdrop: List<Pair<String, String?>>,
    listState: LazyListState,
    arrangement: LandscapeDetailArrangement,
    items: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
    backdropDescription: String? = null,
    listVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    edgeToEdgeItems: Boolean = false,
    pane: @Composable ColumnScope.() -> Unit = {},
) {
    if (backdropStyle == BackdropStyle.BlurredCover) {
        LandscapeDetailBody(
            backdropStyle,
            backdrop,
            listState,
            arrangement,
            items,
            modifier,
            backdropDescription,
            listVerticalArrangement,
            edgeToEdgeItems,
            pane,
        )
    } else {
        LandscapeDetailTheme {
            LandscapeDetailBody(
                backdropStyle,
                backdrop,
                listState,
                arrangement,
                items,
                modifier,
                backdropDescription,
                listVerticalArrangement,
                edgeToEdgeItems,
                pane,
            )
        }
    }
}

@Composable
private fun LandscapeDetailBody(
    backdropStyle: BackdropStyle,
    backdrop: List<Pair<String, String?>>,
    listState: LazyListState,
    arrangement: LandscapeDetailArrangement,
    items: LazyListScope.() -> Unit,
    modifier: Modifier,
    backdropDescription: String?,
    listVerticalArrangement: Arrangement.Vertical,
    edgeToEdgeItems: Boolean,
    pane: @Composable ColumnScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = detailListBottomPadding()
    val top = statusBarTop + afinityTopAppBarHeight()
    val drawerStartInset = LocalPinnedDrawerStartInset.current
    val layoutDirection = LocalLayoutDirection.current
    val safeHorizontal =
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val safeStart = safeHorizontal.calculateStartPadding(layoutDirection)
    val safeEnd = safeHorizontal.calculateEndPadding(layoutDirection)
    val horizontalSafeViewport =
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))

    Box(modifier = modifier.fillMaxSize()) {
        DetailBackdrop(backdropStyle, backdrop, backdropDescription)

        when (arrangement) {
            LandscapeDetailArrangement.Single ->
                if (edgeToEdgeItems) {
                    val edgePadding =
                        DetailEdgePadding(
                            start = safeStart + drawerStartInset + DetailGutter,
                            end = safeEnd + DetailGutter,
                        )
                    CompositionLocalProvider(LocalDetailEdgePadding provides edgePadding) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = top, bottom = bottom),
                            verticalArrangement = listVerticalArrangement,
                            content = items,
                        )
                    }
                } else {
                    CompositionLocalProvider(
                        LocalDetailEdgePadding provides DetailEdgePadding.Zero,
                        LocalPinnedDrawerStartInset provides 0.dp,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = horizontalSafeViewport,
                            contentPadding =
                                PaddingValues(
                                    start = DetailGutter + drawerStartInset,
                                    top = top,
                                    end = DetailGutter,
                                    bottom = bottom,
                                ),
                            verticalArrangement = listVerticalArrangement,
                            content = items,
                        )
                    }
                }

            LandscapeDetailArrangement.Split -> {
                val panelColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                val endInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.End)
                CompositionLocalProvider(
                    LocalDetailEdgePadding provides DetailEdgePadding.Zero,
                    LocalPinnedDrawerStartInset provides 0.dp,
                ) {
                    Row(modifier = horizontalSafeViewport.padding(start = drawerStartInset)) {
                        Column(
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(
                                        start = DetailGutter,
                                        top = top,
                                        end = DetailGutter,
                                        bottom = bottom,
                                    ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = pane,
                        )
                        LazyColumn(
                            state = listState,
                            modifier =
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .panelToEdges(panelColor, WindowInsets(0), endInsets),
                            contentPadding = PaddingValues(top = top, bottom = bottom),
                            verticalArrangement = listVerticalArrangement,
                            content = items,
                        )
                    }
                }
            }

            is LandscapeDetailArrangement.Sidebar ->
                CompositionLocalProvider(
                    LocalDetailEdgePadding provides DetailEdgePadding.Zero,
                    LocalPinnedDrawerStartInset provides 0.dp,
                ) {
                    Row(
                        modifier =
                            horizontalSafeViewport
                                .padding(start = drawerStartInset)
                                .padding(horizontal = DetailGutter),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(
                            modifier =
                                Modifier.width(arrangement.width)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = top, bottom = bottom),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = pane,
                        )
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(top = top, bottom = bottom),
                            verticalArrangement = listVerticalArrangement,
                            content = items,
                        )
                    }
                }
        }
    }
}

private fun Modifier.panelToEdges(color: Color, top: WindowInsets, end: WindowInsets) = drawBehind {
    val topPx = top.getTop(this).toFloat()
    val endPx = (end.getLeft(this, layoutDirection) + end.getRight(this, layoutDirection)).toFloat()
    val left = if (layoutDirection == LayoutDirection.Ltr) 0f else -endPx
    drawRect(
        color = color,
        topLeft = Offset(left, -topPx),
        size = Size(size.width + endPx, size.height + topPx),
    )
}
