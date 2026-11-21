package com.makd.afinity.navigation

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationSuiteStateTest {

    @Test
    fun targetNavigationSuiteValueMapsVisibilityBoolean() {
        assertEquals(NavigationSuiteScaffoldValue.Visible, targetNavigationSuiteValue(true))
        assertEquals(NavigationSuiteScaffoldValue.Hidden, targetNavigationSuiteValue(false))
    }

    @Test
    fun directStateUpdatesImmediatelyInBothDirectionsWithoutAnimationDelay() = runBlocking {
        val state = DirectNavigationSuiteScaffoldState(NavigationSuiteScaffoldValue.Visible)
        assertEquals(NavigationSuiteScaffoldValue.Visible, state.currentValue)
        assertEquals(NavigationSuiteScaffoldValue.Visible, state.targetValue)
        assertFalse(state.isAnimating)

        state.update(false)
        assertEquals(NavigationSuiteScaffoldValue.Hidden, state.currentValue)
        assertEquals(NavigationSuiteScaffoldValue.Hidden, state.targetValue)

        state.update(true)
        assertEquals(NavigationSuiteScaffoldValue.Visible, state.currentValue)
        assertEquals(NavigationSuiteScaffoldValue.Visible, state.targetValue)

        state.hide()
        assertEquals(NavigationSuiteScaffoldValue.Hidden, state.currentValue)

        state.show()
        assertEquals(NavigationSuiteScaffoldValue.Visible, state.currentValue)

        state.toggle()
        assertEquals(NavigationSuiteScaffoldValue.Hidden, state.currentValue)

        state.toggle()
        assertEquals(NavigationSuiteScaffoldValue.Visible, state.currentValue)

        state.snapTo(NavigationSuiteScaffoldValue.Hidden)
        assertEquals(NavigationSuiteScaffoldValue.Hidden, state.currentValue)
    }

    @Test
    fun resolveLastTopLevelRoutePreservesSelectedTabWhileSlidingOutIntoSubscreen() {
        assertNull(resolveLastTopLevelRoute(Destination.SPLASH_ROUTE, null))

        val onHome = resolveLastTopLevelRoute(Destination.HOME.route, null)
        assertEquals(Destination.HOME.route, onHome)

        // Entering a sub-screen preserves the Home tab highlight while the bar slides away
        val onItemDetail =
            resolveLastTopLevelRoute(Destination.createItemDetailRoute("abc"), onHome)
        assertEquals(Destination.HOME.route, onItemDetail)

        // Switching to Favorites updates the active top-level route
        val onFavorites = resolveLastTopLevelRoute(Destination.FAVORITES.route, onItemDetail)
        assertEquals(Destination.FAVORITES.route, onFavorites)

        // Entering a category sub-screen from Favorites preserves Favorites highlight
        val onFavoritesCategory =
            resolveLastTopLevelRoute(
                Destination.createFavoritesCategoryRoute("MOVIES"),
                onFavorites,
            )
        assertEquals(Destination.FAVORITES.route, onFavoritesCategory)
    }
}
