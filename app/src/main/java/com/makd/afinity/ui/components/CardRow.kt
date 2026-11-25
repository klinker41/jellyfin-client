package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.utils.LocalCardRowFocusFirstOnEnter
import com.makd.afinity.ui.utils.LocalCardRowInitialFocusRequester
import com.makd.afinity.ui.utils.LocalIsTelevision
import com.makd.afinity.ui.utils.tvFocusFirstOnEnter

@Composable
fun CardRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberCardRowListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    val initialFocusRequester = LocalCardRowInitialFocusRequester.current
    val focusFirstOnEnter = LocalCardRowFocusFirstOnEnter.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
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
    val rowFocusModifier =
        if (focusFirstOnEnter) {
            Modifier.tvFocusFirstOnEnter(
                firstItemFocusRequester = firstItemFocusRequester,
                coroutineScope = coroutineScope,
                isScrolledPastFirst = { state.firstVisibleItemIndex > 0 },
                scrollToFirst = { state.scrollToItem(0) },
            )
        } else {
            Modifier.focusRestorer()
        }

    LazyRow(
        modifier =
            modifier
                .then(
                    if (initialFocusRequester != null)
                        Modifier.focusRequester(initialFocusRequester)
                    else Modifier,
                )
                .then(rowFocusModifier),
        state = state,
        contentPadding = effectiveContentPadding,
        horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
        content =
            if (focusFirstOnEnter) {
                { FirstItemFocusLazyListScope(this, firstItemFocusRequester).content() }
            } else {
                content
            },
    )
}

private class FirstItemFocusLazyListScope(
    private val delegate: LazyListScope,
    private val firstItemFocusRequester: FocusRequester,
) : LazyListScope by delegate {
    private var hasMarkedFirst = false

    override fun item(
        key: Any?,
        contentType: Any?,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        if (!hasMarkedFirst) {
            hasMarkedFirst = true
            val requester = firstItemFocusRequester
            delegate.item(key = key, contentType = contentType) {
                Box(modifier = Modifier.focusRequester(requester)) { content() }
            }
        } else {
            delegate.item(key = key, contentType = contentType, content = content)
        }
    }

    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    override fun item(key: Any?, content: @Composable LazyItemScope.() -> Unit) {
        item(key = key, contentType = null, content = content)
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        if (!hasMarkedFirst && count > 0) {
            hasMarkedFirst = true
            val requester = firstItemFocusRequester
            delegate.items(count = count, key = key, contentType = contentType) { index ->
                if (index == 0) {
                    Box(modifier = Modifier.focusRequester(requester)) { itemContent(index) }
                } else {
                    itemContent(index)
                }
            }
        } else {
            delegate.items(
                count = count,
                key = key,
                contentType = contentType,
                itemContent = itemContent,
            )
        }
    }

    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        items(count = count, key = key, contentType = { null }, itemContent = itemContent)
    }
}
