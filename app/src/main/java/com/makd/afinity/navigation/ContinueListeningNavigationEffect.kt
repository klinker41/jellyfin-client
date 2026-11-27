package com.makd.afinity.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import com.makd.afinity.player.ContinueListeningQueue
import com.makd.afinity.player.ContinueListeningQueueItem

@Composable
fun ContinueListeningNavigationEffect(
    continueListeningQueue: ContinueListeningQueue,
    navController: NavController,
) {
    LaunchedEffect(continueListeningQueue, navController) {
        continueListeningQueue.advancedTo.collect { pending ->
            val currentRoute = navController.currentDestination?.route
            when (val nextItem = pending.item) {
                is ContinueListeningQueueItem.Audiobook -> {
                    if (currentRoute == Destination.MUSIC_PLAYER_ROUTE) {
                        navController.navigate(
                            Destination.createAudiobookshelfPlayerRoute(
                                itemId = nextItem.bookId.toString(),
                                source = Destination.AUDIOBOOK_SOURCE_JELLYFIN,
                                autoPlay = true,
                            ),
                        ) {
                            popUpTo(Destination.MUSIC_PLAYER_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                is ContinueListeningQueueItem.PodcastEpisode -> {
                    if (currentRoute?.startsWith("audiobookshelf/player/") == true) {
                        navController.navigate(Destination.MUSIC_PLAYER_ROUTE) {
                            popUpTo(currentRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
            continueListeningQueue.finishAdvancing(pending.generation)
        }
    }
}
