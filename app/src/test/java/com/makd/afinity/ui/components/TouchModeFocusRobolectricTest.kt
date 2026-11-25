package com.makd.afinity.ui.components

import android.app.Application
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.makd.afinity.R
import com.makd.afinity.data.manager.Connectivity
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.theme.ThemeMode
import com.makd.afinity.ui.utils.LocalIsTelevision
import com.makd.afinity.ui.utils.LocalTopBarButtonsFocusable
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TouchModeFocusRobolectricTest {

    private val touchModeResetRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                try {
                    base.evaluate()
                } finally {
                    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
                }
            }
        }
    }

    val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(touchModeResetRule).around(composeTestRule)

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun `NavigableListDetailPaneScaffold does not focus AfinityBackButton in touch mode on phone`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val backDesc = context.getString(R.string.cd_back)
        lateinit var inputModeManager: InputModeManager
        var buttonsFocusable by mutableStateOf(true)

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                inputModeManager = LocalInputModeManager.current
                val directive = listDetailPaneDirective()
                val navigator =
                    rememberListDetailPaneScaffoldNavigator<String>(
                        scaffoldDirective = directive,
                        initialDestinationHistory =
                            listOf(
                                ThreePaneScaffoldDestinationItem(
                                    ListDetailPaneScaffoldRole.List,
                                    null,
                                ),
                            ),
                    )
                CompositionLocalProvider(
                    LocalIsTelevision provides false,
                    LocalTopBarButtonsFocusable provides buttonsFocusable,
                ) {
                    NavigableListDetailPaneScaffold(
                        navigator = navigator,
                        listPane = {
                            AnimatedPane {
                                AfinityScreen(
                                    topBar = { backgroundOpacity ->
                                        AfinityTopBar(
                                            title = { AfinityTopAppBarTitle("Settings") },
                                            navigationIcon = { AfinityBackButton(onClick = {}) },
                                            backgroundOpacity = backgroundOpacity,
                                        )
                                    },
                                ) {
                                    Text("Settings body")
                                }
                            }
                        },
                        detailPane = { AnimatedPane { Text("Detail") } },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(InputMode.Touch, inputModeManager.inputMode)
        composeTestRule.onNodeWithContentDescription(backDesc).assertIsNotFocused()

        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(backDesc)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.onNodeWithContentDescription(backDesc).assertIsFocused()

        buttonsFocusable = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(backDesc).assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun `AfinityTopAppBar buttons do not take focus in touch mode on non-TV`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val menuDesc = context.getString(R.string.cd_open_navigation_menu)
        val diceDesc = context.getString(R.string.cd_random_item)
        val searchDesc = context.getString(R.string.cd_search_icon)
        val profileDesc = context.getString(R.string.cd_profile_icon)
        val offlineModeManager =
            mockk<OfflineModeManager>(relaxed = true) {
                every { connectionType } returns MutableStateFlow(ConnectionType.LOCAL)
                every { connectivity } returns MutableStateFlow(Connectivity.Online)
            }
        val topAppBarViewModel =
            AfinityTopAppBarViewModel(
                offlineModeManager = offlineModeManager,
                serverRepository = mockk<ServerRepository>(relaxed = true),
                preferencesRepository = mockk<PreferencesRepository>(relaxed = true),
            )
        lateinit var inputModeManager: InputModeManager
        val wrapperFocusRequester = FocusRequester()

        composeTestRule.setContent {
            PreviewHost(modifier = Modifier.fillMaxSize(), themeMode = ThemeMode.DARK) {
                inputModeManager = LocalInputModeManager.current
                CompositionLocalProvider(LocalIsTelevision provides false) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .focusRequester(wrapperFocusRequester)
                                .focusGroup(),
                    ) {
                        AfinityTopAppBar(
                            title = {},
                            onMenuClick = {},
                            onRandomClick = {},
                            onSearchClick = {},
                            profile = AppBarProfile(onClick = {}, name = null, imageUrl = null),
                            viewModel = topAppBarViewModel,
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(InputMode.Touch, inputModeManager.inputMode)

        composeTestRule.runOnIdle { wrapperFocusRequester.requestFocus() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(menuDesc).assertIsNotFocused()
        composeTestRule.onNodeWithContentDescription(diceDesc).assertIsNotFocused()
        composeTestRule.onNodeWithContentDescription(searchDesc).assertIsNotFocused()
        composeTestRule.onNodeWithContentDescription(profileDesc).assertIsNotFocused()
    }
}
