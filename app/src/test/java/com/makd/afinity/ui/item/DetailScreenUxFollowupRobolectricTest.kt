package com.makd.afinity.ui.item

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.testutil.TestMediaFixtures.testMovie
import com.makd.afinity.testutil.TestMediaFixtures.testPerson
import com.makd.afinity.testutil.TestMediaFixtures.testSeason
import com.makd.afinity.testutil.TestMediaFixtures.testUuid
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.BackdropStyle
import com.makd.afinity.ui.components.DetailGutter
import com.makd.afinity.ui.components.ImageHeroKind
import com.makd.afinity.ui.components.LandscapeDetailArrangement
import com.makd.afinity.ui.components.LandscapeDetailLayout
import com.makd.afinity.ui.components.LocalPinnedDrawerStartInset
import com.makd.afinity.ui.components.PinnedDrawerContentContainer
import com.makd.afinity.ui.components.PinnedDrawerStartOccupancy
import com.makd.afinity.ui.components.PortraitDetailLayout
import com.makd.afinity.ui.components.PortraitHero
import com.makd.afinity.ui.components.TopBarBlurState
import com.makd.afinity.ui.components.listDetailPaneDirective
import com.makd.afinity.ui.components.rememberDetailTopBarBlurState
import com.makd.afinity.ui.item.components.SeasonsSection
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.SimilarItemsSection
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.item.components.shared.detailRowItem
import com.makd.afinity.ui.preview.PreviewHost
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DetailScreenUxFollowupRobolectricTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `portrait phone detail rows scroll edge to edge while headers keep 16dp gutter`() {
        val windowWidth = 360.dp
        val movies = (1..8).map { testMovie(id = testUuid(it), name = "Similar $it") }

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(windowWidth)) {
                val listState = rememberLazyListState()
                PortraitDetailLayout(
                    hero = PortraitHero.Image(ImageHeroKind.Backdrop, emptyList()),
                    listState = listState,
                    edgeToEdgeItems = true,
                    header = { Text("Dune") },
                    items = {
                        detailRowItem("similar") {
                            SimilarItemsSection(
                                items = movies,
                                onItemClick = {},
                                widthSizeClass = WindowWidthSizeClass.Compact,
                            )
                        }
                    },
                )
            }
        }

        val headerBounds = composeTestRule.onNodeWithText("Dune").getBoundsInRoot()
        assertDpEquals(DetailGutter, headerBounds.left)

        val sectionTitleBounds = composeTestRule.onNodeWithText("More Like This").getBoundsInRoot()
        assertDpEquals(DetailGutter, sectionTitleBounds.left)

        val rowBounds = composeTestRule.onNode(hasHorizontalScrollAction()).getBoundsInRoot()
        assertDpEquals(0.dp, rowBounds.left)
        assertDpEquals(windowWidth, rowBounds.right)

        val firstCardBounds = composeTestRule.onNodeWithText("Similar 1").getBoundsInRoot()
        assertDpEquals(DetailGutter, firstCardBounds.left)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `portrait unfolded detail aligns headers past pinned drawer while rows scroll behind it`() {
        val windowWidth = 673.dp
        val expectedStart = PinnedDrawerStartOccupancy + DetailGutter
        val seasons = (1..8).map { testSeason(id = testUuid(100 + it), name = "Season $it") }

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(windowWidth)) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    val listState = rememberLazyListState()
                    PortraitDetailLayout(
                        hero = PortraitHero.Image(ImageHeroKind.Backdrop, emptyList()),
                        listState = listState,
                        edgeToEdgeItems = true,
                        header = { Text("Severance") },
                        items = {
                            detailRowItem("seasons") {
                                SeasonsSection(
                                    seasons = seasons,
                                    onSeasonClick = {},
                                    widthSizeClass = WindowWidthSizeClass.Medium,
                                )
                            }
                        },
                    )
                }
            }
        }

        val headerBounds = composeTestRule.onNodeWithText("Severance").getBoundsInRoot()
        assertDpEquals(expectedStart, headerBounds.left)

        val sectionTitleBounds = composeTestRule.onNodeWithText("Seasons").getBoundsInRoot()
        assertDpEquals(expectedStart, sectionTitleBounds.left)

        val rowBounds = composeTestRule.onNode(hasHorizontalScrollAction()).getBoundsInRoot()
        assertDpEquals(0.dp, rowBounds.left)
        assertDpEquals(windowWidth, rowBounds.right)

        val firstSeasonBounds = composeTestRule.onNodeWithText("Season 1").getBoundsInRoot()
        assertDpEquals(expectedStart, firstSeasonBounds.left)
    }

    @Test
    @Config(qualifiers = "w841dp-h673dp")
    fun `landscape unfolded detail aligns content past pinned drawer and scrolls rows edge to edge`() {
        val windowWidth = 841.dp
        val expectedStart = PinnedDrawerStartOccupancy + DetailGutter
        val people = (1..10).map { testPerson(id = testUuid(200 + it), name = "Actor $it") }
        val movie = testMovie(people = people)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(windowWidth)) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    val listState = rememberLazyListState()
                    LandscapeDetailLayout(
                        backdropStyle = BackdropStyle.Cinematic,
                        backdrop = emptyList(),
                        listState = listState,
                        arrangement = LandscapeDetailArrangement.Single,
                        edgeToEdgeItems = true,
                        items = {
                            detailItem(
                                key = "header",
                                horizontalPadding = DetailGutter,
                                gap = 0.dp,
                            ) {
                                Text("Landscape Header")
                            }
                            detailRowItem("cast") {
                                CastSection(
                                    item = movie,
                                    onPersonClick = {},
                                    widthSizeClass = WindowWidthSizeClass.Expanded,
                                )
                            }
                        },
                    )
                }
            }
        }

        val headerBounds = composeTestRule.onNodeWithText("Landscape Header").getBoundsInRoot()
        assertDpEquals(expectedStart, headerBounds.left)

        val castHeaderBounds = composeTestRule.onNodeWithText("Cast").getBoundsInRoot()
        assertDpEquals(expectedStart, castHeaderBounds.left)

        val rowBounds = composeTestRule.onNode(hasHorizontalScrollAction()).getBoundsInRoot()
        assertDpEquals(0.dp, rowBounds.left)
        assertDpEquals(windowWidth, rowBounds.right)

        val firstActorBounds = composeTestRule.onNodeWithText("Actor 1").getBoundsInRoot()
        assertDpEquals(expectedStart, firstActorBounds.left)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `DetailItemBox resets edge locals so nested rows inside padded boxes do not double-pad`() {
        val expectedStart = PinnedDrawerStartOccupancy + DetailGutter
        val movie =
            testMovie(
                people = listOf(testPerson(name = "Nested Actor")),
                externalUrls = emptyList(),
            )

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(673.dp)) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    val listState = rememberLazyListState()
                    PortraitDetailLayout(
                        hero = PortraitHero.Image(ImageHeroKind.Backdrop, emptyList()),
                        listState = listState,
                        edgeToEdgeItems = true,
                        header = {
                            CastSection(
                                item = movie,
                                onPersonClick = {},
                                widthSizeClass = WindowWidthSizeClass.Medium,
                            )
                        },
                        items = {
                            detailItem("nested_cast", DetailGutter) {
                                CastSection(
                                    item = movie,
                                    onPersonClick = {},
                                    widthSizeClass = WindowWidthSizeClass.Medium,
                                )
                            }
                            detailItem("links", DetailGutter) { ExternalLinksSection(item = movie) }
                        },
                    )
                }
            }
        }

        val nestedNodes = composeTestRule.onAllNodesWithText("Nested Actor", substring = true)
        assertEquals(2, nestedNodes.fetchSemanticsNodes().size)
        assertDpEquals(expectedStart, nestedNodes[0].getBoundsInRoot().left)
        assertDpEquals(expectedStart, nestedNodes[1].getBoundsInRoot().left)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `portrait detail screen activates progressive top bar blur on scroll and clears at top`() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        lateinit var blurState: TopBarBlurState
        lateinit var hostView: View
        var fullThresholdPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                hostView = LocalView.current
                fullThresholdPx = with(LocalDensity.current) { 450.dp.toPx().roundToInt() }
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                blurState = rememberDetailTopBarBlurState(listState, hasHeroSlot = true)

                AfinityScreen(
                    topBar = { backgroundOpacity ->
                        AfinityTopBar(
                            title = { AfinityTopAppBarTitle("Movie") },
                            backgroundOpacity = backgroundOpacity,
                        )
                    },
                    mode = AfinityScreenMode.Hero,
                    topBarBlurState = blurState,
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        item(key = "hero") {
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth().height(500.dp).background(Color.Red),
                            )
                        }
                        items(10) { idx ->
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Text("Detail Row $idx")
                            }
                        }
                    }
                }
            }
        }

        pumpDraw(hostView)
        assertFalse(blurState.isBlurActive)
        assertNull(blurState.blurLayer.renderEffect)

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = fullThresholdPx) }
        }
        pumpDraw(hostView)
        assertTrue(blurState.isBlurActive)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = 0) }
        }
        pumpDraw(hostView)
        assertFalse(blurState.isBlurActive)
        assertNull(blurState.blurLayer.renderEffect)
    }

    @Test
    @Config(qualifiers = "w841dp-h673dp")
    fun `landscape detail screen activates progressive top bar blur on scroll`() {
        lateinit var listState: LazyListState
        lateinit var scope: CoroutineScope
        lateinit var blurState: TopBarBlurState
        lateinit var hostView: View
        var scrollPx = 0

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                hostView = LocalView.current
                scrollPx = with(LocalDensity.current) { 80.dp.toPx().roundToInt() }
                listState = rememberLazyListState()
                scope = rememberCoroutineScope()
                blurState = rememberDetailTopBarBlurState(listState, hasHeroSlot = false)

                AfinityScreen(
                    topBar = { backgroundOpacity ->
                        AfinityTopBar(
                            title = { AfinityTopAppBarTitle("Movie") },
                            backgroundOpacity = backgroundOpacity,
                        )
                    },
                    mode = AfinityScreenMode.Hero,
                    topBarBlurState = blurState,
                    topBarBlurContainerColor = Color.Black,
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(12) { idx ->
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Text("Row $idx")
                            }
                        }
                    }
                }
            }
        }

        pumpDraw(hostView)
        assertFalse(blurState.isBlurActive)

        composeTestRule.runOnIdle {
            scope.launch { listState.scrollToItem(index = 0, scrollOffset = scrollPx) }
        }
        pumpDraw(hostView)
        assertTrue(blurState.isBlurActive)
        assertTrue(blurState.hasRecordedContent)
        assertNotNull(blurState.blurLayer.renderEffect)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `AfinityScreen Standard mode insets content by pinned drawer only when requested`() {
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    AfinityScreen(
                        topBar = { AfinityTopBar(title = { AfinityTopAppBarTitle("Genres") }) },
                        mode = AfinityScreenMode.Standard,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("std_content"),
                        ) {
                            Text("Genre Body")
                        }
                    }
                }
            }
        }

        val contentBounds = composeTestRule.onNodeWithTag("std_content").getBoundsInRoot()
        assertDpEquals(PinnedDrawerStartOccupancy, contentBounds.left)
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `AfinityScreen Standard mode skips drawer inset when insetContentForPinnedDrawer is false`() {
        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    AfinityScreen(
                        topBar = { AfinityTopBar(title = { AfinityTopAppBarTitle("Favorites") }) },
                        mode = AfinityScreenMode.Standard,
                        insetContentForPinnedDrawer = false,
                    ) {
                        Box(
                            modifier =
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("uninset_content"),
                        ) {
                            Text("Favorites Body")
                        }
                    }
                }
            }
        }

        val contentBounds = composeTestRule.onNodeWithTag("uninset_content").getBoundsInRoot()
        assertDpEquals(0.dp, contentBounds.left)
    }

    @Test
    @Config(qualifiers = "w840dp-h673dp")
    fun `PinnedDrawerContentContainer clears start inset while preserving window inset for listDetailPaneDirective`() {
        var nestedStartInset = (-1).dp
        var maxPartitions = -1

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    PinnedDrawerContentContainer(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                    ) {
                        nestedStartInset = LocalPinnedDrawerStartInset.current
                        maxPartitions = listDetailPaneDirective().maxHorizontalPartitions
                    }
                }
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(0.dp, nestedStartInset)
            assertEquals(1, maxPartitions)
        }
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun `portrait detail layout without edgeToEdgeItems clears pinned drawer on regular items without double padding`() {
        val expectedStart = PinnedDrawerStartOccupancy + DetailGutter
        val externalUrls =
            listOf(AfinityExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt1234567"))

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.width(673.dp)) {
                CompositionLocalProvider(
                    LocalPinnedDrawerStartInset provides PinnedDrawerStartOccupancy,
                ) {
                    val listState = rememberLazyListState()
                    PortraitDetailLayout(
                        hero = PortraitHero.Cover(emptyList()),
                        listState = listState,
                        header = { Text("Album Header", modifier = Modifier.fillMaxWidth()) },
                        items = {
                            item(key = "album_info") {
                                Column(modifier = Modifier.padding(horizontal = DetailGutter)) {
                                    Text("Album Description")
                                    ExternalLinksSection(externalUrls = externalUrls)
                                }
                            }
                        },
                    )
                }
            }
        }

        val headerBounds = composeTestRule.onNodeWithText("Album Header").getBoundsInRoot()
        val overviewBounds = composeTestRule.onNodeWithText("Album Description").getBoundsInRoot()
        val linksBounds = composeTestRule.onNodeWithContentDescription("IMDb").getBoundsInRoot()
        assertDpEquals(expectedStart, headerBounds.left)
        assertDpEquals(expectedStart, overviewBounds.left)
        assertDpEquals(expectedStart, linksBounds.left)
    }

    private fun pumpDraw(hostView: View) {
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            val bitmap =
                Bitmap.createBitmap(
                    hostView.width.coerceAtLeast(1),
                    hostView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
            hostView.draw(Canvas(bitmap))
            bitmap.recycle()
        }
    }

    private fun hasHorizontalScrollAction(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    private fun assertDpEquals(expected: Dp, actual: Dp, tolerance: Dp = 1.dp) {
        assertEquals(expected.value, actual.value, tolerance.value)
    }
}
