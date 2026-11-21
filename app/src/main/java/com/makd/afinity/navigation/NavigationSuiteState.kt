package com.makd.afinity.navigation

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal fun targetNavigationSuiteValue(visible: Boolean): NavigationSuiteScaffoldValue =
    if (visible) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden

internal fun resolveLastTopLevelRoute(
    currentRoute: String?,
    previousTopLevelRoute: String?,
): String? =
    if (currentRoute != null && Destination.entries.any { it.route == currentRoute }) {
        currentRoute
    } else {
        previousTopLevelRoute
    }

@Stable
internal class DirectNavigationSuiteScaffoldState(
    initialValue: NavigationSuiteScaffoldValue,
) : NavigationSuiteScaffoldState {
    private var value by mutableStateOf(initialValue)

    override val isAnimating: Boolean = false
    override val targetValue: NavigationSuiteScaffoldValue
        get() = value

    override val currentValue: NavigationSuiteScaffoldValue
        get() = value

    fun update(visible: Boolean) {
        value = targetNavigationSuiteValue(visible)
    }

    override suspend fun hide() {
        value = NavigationSuiteScaffoldValue.Hidden
    }

    override suspend fun show() {
        value = NavigationSuiteScaffoldValue.Visible
    }

    override suspend fun toggle() {
        value =
            if (value == NavigationSuiteScaffoldValue.Visible) {
                NavigationSuiteScaffoldValue.Hidden
            } else {
                NavigationSuiteScaffoldValue.Visible
            }
    }

    override suspend fun snapTo(targetValue: NavigationSuiteScaffoldValue) {
        value = targetValue
    }
}

private class RouteHolder(var value: String? = null)

@Composable
internal fun rememberAnimatedNavigationSuiteState(
    visible: Boolean,
): NavigationSuiteScaffoldState {
    val state = remember { DirectNavigationSuiteScaffoldState(targetNavigationSuiteValue(visible)) }
    SideEffect { state.update(visible) }
    return state
}

@Composable
internal fun rememberActiveNavigationRoute(currentRoute: String?): String? {
    val holder = remember { RouteHolder() }
    val resolved = resolveLastTopLevelRoute(currentRoute, holder.value)
    SideEffect { holder.value = resolved }
    return resolved
}
