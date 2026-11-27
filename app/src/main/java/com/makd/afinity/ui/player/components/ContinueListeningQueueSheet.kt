package com.makd.afinity.ui.player.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.player.ContinueListeningQueueItem
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.rememberAfinitySheetState
import java.util.concurrent.atomic.AtomicInteger
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueListeningQueueSheet(
    nowPlayingTitle: String,
    nowPlayingSubtitle: String?,
    nowPlayingCoverUrl: String?,
    upcomingItems: List<ContinueListeningQueueItem>,
    onItemClick: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberAfinitySheetState()

    AfinityModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_continue_listening),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = {
                        onClear()
                        onDismiss()
                    },
                ) {
                    Text(
                        stringResource(R.string.music_filter_clear),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.music_queue_now_playing),
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                ContinueListeningQueueRow(
                    title = nowPlayingTitle,
                    subtitle = nowPlayingSubtitle,
                    coverUrl = nowPlayingCoverUrl,
                    blurHash = null,
                    typeIconRes = R.drawable.ic_book_audio,
                    isCurrentItem = true,
                    onClick = onDismiss,
                )
            }

            if (upcomingItems.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.player_up_next),
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                )

                ContinueListeningUpcomingList(
                    items = upcomingItems,
                    onItemClick = onItemClick,
                    onMoveItem = onMoveItem,
                    onRemoveItem = onRemoveItem,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

private data class ContinueListeningRowEntry(
    val uid: Int,
    val item: ContinueListeningQueueItem,
)

@Composable
fun ContinueListeningUpcomingList(
    items: List<ContinueListeningQueueItem>,
    onItemClick: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragEndIndex by remember { mutableStateOf<Int?>(null) }
    val uidCounter = remember { AtomicInteger(0) }
    var localItems by remember {
        mutableStateOf(
            items.map { item -> ContinueListeningRowEntry(uidCounter.getAndIncrement(), item) },
        )
    }
    LaunchedEffect(items) {
        val pool = localItems.groupByTo(HashMap()) { it.item.itemId }
        localItems = items.map { item ->
            val reused = pool[item.itemId]?.removeFirstOrNull()
            ContinueListeningRowEntry(reused?.uid ?: uidCounter.getAndIncrement(), item)
        }
    }
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            if (dragStartIndex == null) dragStartIndex = from.index
            dragEndIndex = to.index
            localItems = localItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        itemsIndexed(localItems, key = { _, entry -> entry.uid }) { index, entry ->
            val item = entry.item
            ReorderableItem(reorderState, key = entry.uid) { isDragging ->
                val elevation by
                    animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")
                val dismissState = rememberSwipeToDismissBoxState()

                LaunchedEffect(dismissState.currentValue) {
                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        onRemoveItem(index)
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        val color by
                            animateColorAsState(
                                targetValue =
                                    when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart ->
                                            MaterialTheme.colorScheme.errorContainer.copy(
                                                alpha = 0.6f,
                                            )
                                        else -> Color.Transparent
                                    },
                                label = "dismiss_color",
                            )
                        val iconAlpha by
                            animateFloatAsState(
                                targetValue =
                                    if (
                                        dismissState.targetValue ==
                                            SwipeToDismissBoxValue.EndToStart
                                    )
                                        1f
                                    else 0f,
                                label = "dismiss_alpha",
                            )

                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.cd_music_remove),
                                tint =
                                    MaterialTheme.colorScheme.onErrorContainer.copy(
                                        alpha = iconAlpha,
                                    ),
                            )
                        }
                    },
                ) {
                    val typeIcon =
                        when (item) {
                            is ContinueListeningQueueItem.Audiobook -> R.drawable.ic_book_audio
                            is ContinueListeningQueueItem.PodcastEpisode ->
                                R.drawable.ic_apple_podcast
                        }
                    ContinueListeningQueueRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        coverUrl = item.coverUrl,
                        blurHash = item.blurHash,
                        typeIconRes = typeIcon,
                        isCurrentItem = false,
                        onClick = { onItemClick(index) },
                        dragHandleModifier =
                            Modifier.draggableHandle(
                                onDragStopped = {
                                    val fromLocal = dragStartIndex
                                    val toLocal = dragEndIndex
                                    dragStartIndex = null
                                    dragEndIndex = null
                                    if (
                                        fromLocal != null && toLocal != null && fromLocal != toLocal
                                    ) {
                                        onMoveItem(fromLocal, toLocal)
                                    }
                                },
                            ),
                        elevation = elevation,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningQueueRow(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    blurHash: String?,
    typeIconRes: Int,
    isCurrentItem: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                    onClick = onClick,
                ),
        color =
            if (elevation > 0.dp) MaterialTheme.colorScheme.surfaceContainerHigh
            else Color.Transparent,
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        imageUrl = coverUrl,
                        contentDescription = null,
                        blurHash = blurHash,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().aspectRatio(1f),
                        targetWidth = 48.dp,
                        targetHeight = 48.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(typeIconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (isCurrentItem) {
                    Box(
                        modifier =
                            Modifier.matchParentSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_volume_up),
                            contentDescription = stringResource(R.string.cd_music_now_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isCurrentItem) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color =
                        if (isCurrentItem) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!isCurrentItem || !subtitle.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isCurrentItem) {
                            Icon(
                                painter = painterResource(typeIconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            if (!subtitle.isNullOrEmpty()) {
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (!isCurrentItem) {
                Icon(
                    painter = painterResource(R.drawable.ic_swap_vert),
                    contentDescription = stringResource(R.string.cd_music_drag_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier =
                        dragHandleModifier
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            .size(24.dp),
                )
            }
        }
    }
}
