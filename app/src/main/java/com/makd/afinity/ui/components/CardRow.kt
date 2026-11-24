package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.LocalCardRowInitialFocusRequester
import com.makd.afinity.ui.utils.LocalIsTelevision

@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberCardRowListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val initialFocusRequester = LocalCardRowInitialFocusRequester.current
    val layoutDirection = LocalLayoutDirection.current
    val effectiveContentPadding =
        if (LocalIsTelevision.current) {
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top =
                    maxOf(
                        contentPadding.calculateTopPadding(),
                        CardDimensions.TvRowFocusVerticalPadding,
                    ),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom =
                    maxOf(
                        contentPadding.calculateBottomPadding(),
                        CardDimensions.TvRowFocusVerticalPadding,
                    ),
            )
        } else {
            contentPadding
        }
    LazyRow(
        modifier =
            modifier
                .then(
                    if (initialFocusRequester != null)
                        Modifier.focusRequester(initialFocusRequester)
                    else Modifier,
                )
                .focusRestorer(),
        state = state,
        contentPadding = effectiveContentPadding,
        horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
        content = content,
    )
}
