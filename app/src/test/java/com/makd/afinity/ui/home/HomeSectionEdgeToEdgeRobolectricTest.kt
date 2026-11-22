package com.makd.afinity.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.testutil.TestMediaFixtures.testEpisode
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.ui.components.CardRow
import com.makd.afinity.ui.home.components.ItemsRowSection
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.OptimizedContinueWatchingSection
import com.makd.afinity.ui.home.components.OptimizedLatestMoviesSection
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.theme.CardDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp")
class HomeSectionEdgeToEdgeRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `CardRow with RowContentPadding scrolls edge to edge while preserving start and end gutters`() {
        val containerWidth = 360.dp
        val itemCount = 10
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(containerWidth)) {
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                CardRow(
                    state = listState,
                    contentPadding = CardDimensions.RowContentPadding,
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("card_row"),
                ) {
                    items(itemCount) { index ->
                        Box(
                            modifier = Modifier.width(100.dp).height(120.dp).testTag("item_$index"),
                        ) {
                            Text("Item $index")
                        }
                    }
                }
            }
        }

        val rowBounds = composeTestRule.onNodeWithTag("card_row").getBoundsInRoot()
        assertDpEquals(0.dp, rowBounds.left)
        assertDpEquals(containerWidth, rowBounds.right)

        val firstAtRest = composeTestRule.onNodeWithTag("item_0").getBoundsInRoot()
        assertDpEquals(CardDimensions.RowGutter, firstAtRest.left)

        // Item 3 (16 + 3 * 112 = 352.dp..452.dp) extends past the 344.dp inner gutter to the 360.dp
        // edge.
        val rightEdgeAtRest = composeTestRule.onNodeWithTag("item_3").getBoundsInRoot()
        assertDpEquals(containerWidth, rightEdgeAtRest.right)

        // Scroll slightly past the first item so it renders into the start [0.dp, 16.dp) gutter.
        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = 80) }
        }
        composeTestRule.waitForIdle()

        val firstScrolled = composeTestRule.onNodeWithTag("item_0").getBoundsInRoot()
        assertDpEquals(0.dp, firstScrolled.left)
        val unclippedLeft = composeTestRule.onNodeWithTag("item_0").getUnclippedBoundsInRoot().left
        assertTrue(
            "Expected scrolled item to render past the 16.dp start gutter, got unclipped left=$unclippedLeft",
            unclippedLeft < CardDimensions.RowGutter,
        )

        // Scroll to the very end and verify trailing RowGutter (16.dp) after the last item.
        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = itemCount - 1, scrollOffset = 10_000) }
        }
        composeTestRule.waitForIdle()

        val lastAtEnd = composeTestRule.onNodeWithTag("item_${itemCount - 1}").getBoundsInRoot()
        assertDpEquals(containerWidth - CardDimensions.RowGutter, lastAtEnd.right)
    }

    @Test
    fun `ItemsRowSection header stays inset while row scrolls edge to edge`() {
        val movies = (1..8).map { testMovie(id = testUuid(it), name = "Movie $it") }
        assertHomeSectionEdgeToEdge(
            headerText = "Sci-Fi Essentials",
            firstCardText = "Movie 1",
        ) {
            ItemsRowSection(
                title = "Sci-Fi Essentials",
                items = movies,
                sectionKey = "scifi",
                onItemClick = {},
            )
        }
    }

    @Test
    fun `OptimizedContinueWatchingSection header stays inset while row scrolls edge to edge`() {
        val episodes = (1..6).map { testEpisode(id = testUuid(100 + it), name = "Episode $it") }
        assertHomeSectionEdgeToEdge(
            headerText = "Continue Watching",
            firstCardText = "Episode 1",
        ) {
            OptimizedContinueWatchingSection(
                items = episodes,
                onItemClick = {},
                widthSizeClass = WindowWidthSizeClass.Compact,
            )
        }
    }

    @Test
    fun `NextUpSection header stays inset while row scrolls edge to edge`() {
        val episodes = (1..6).map { testEpisode(id = testUuid(200 + it), name = "Next Ep $it") }
        assertHomeSectionEdgeToEdge(
            headerText = "Next Up",
            firstCardText = "Next Ep 1",
        ) {
            NextUpSection(
                episodes = episodes,
                onEpisodeClick = {},
                widthSizeClass = WindowWidthSizeClass.Compact,
            )
        }
    }

    @Test
    fun `OptimizedLatestMoviesSection header stays inset while row scrolls edge to edge`() {
        val movies = (1..8).map { testMovie(id = testUuid(300 + it), name = "Latest $it") }
        assertHomeSectionEdgeToEdge(
            headerText = "Latest Movies",
            firstCardText = "Latest 1",
        ) {
            OptimizedLatestMoviesSection(
                title = "Latest Movies",
                items = movies,
                onItemClick = {},
                widthSizeClass = WindowWidthSizeClass.Compact,
            )
        }
    }

    private fun assertHomeSectionEdgeToEdge(
        headerText: String,
        firstCardText: String,
        content: @Composable () -> Unit,
    ) {
        val containerWidth = 360.dp
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(containerWidth)) { content() }
        }

        val headerBounds = composeTestRule.onNodeWithText(headerText).getBoundsInRoot()
        assertDpEquals(CardDimensions.RowGutter, headerBounds.left)

        val rowNode = composeTestRule.onNode(hasScrollAction())
        val rowBounds = rowNode.getBoundsInRoot()
        assertDpEquals(0.dp, rowBounds.left)
        assertDpEquals(containerWidth, rowBounds.right)

        val firstCardBounds =
            composeTestRule.onNodeWithText(firstCardText, substring = true).getBoundsInRoot()
        assertDpEquals(CardDimensions.RowGutter, firstCardBounds.left)
    }

    private fun assertDpEquals(expected: Dp, actual: Dp, tolerance: Dp = 1.dp) {
        assertEquals(expected.value, actual.value, tolerance.value)
    }
}
