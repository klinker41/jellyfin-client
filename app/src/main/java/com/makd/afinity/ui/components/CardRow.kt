package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.LocalCardRowInitialFocusRequester

@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberCardRowListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val initialFocusRequester = LocalCardRowInitialFocusRequester.current
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
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
        content = content,
    )
}
