package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Hosts modal navigation drawer content inside [ModalWideNavigationRail]'s `header` slot so the
 * rail measures and lays out edge-to-edge without the 20dp trailing padding and 44dp top inset that
 * `WideNavigationRailLayout` reserves for standard rail `content` items.
 */
@Composable
fun AppModalNavigationDrawer(
    state: WideNavigationRailState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalWideNavigationRail(
        state = state,
        modifier = modifier,
        hideOnCollapse = true,
        expandedShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        colors =
            WideNavigationRailDefaults.colors()
                .copy(modalContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        header = content,
        expandedHeaderTopPadding = 0.dp,
        windowInsets = WindowInsets(0.dp),
        contentPadding = PaddingValues(0.dp),
    ) {}
}
