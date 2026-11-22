package com.makd.afinity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PendingNavigationManager
import com.makd.afinity.data.manager.RemoteMessageManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.MainNavigation
import com.makd.afinity.ui.components.LocalStatusBarIcons
import com.makd.afinity.ui.components.RemoteMessagePill
import com.makd.afinity.ui.components.StatusBarIconsEffect
import com.makd.afinity.ui.components.StatusBarIconsState
import com.makd.afinity.ui.theme.AFinityTheme
import com.makd.afinity.ui.theme.ThemeMode
import com.makd.afinity.widget.AudioWidgetIntents
import com.makd.afinity.widget.AudioWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Immutable
private data class ThemePreferences(
    val themeMode: String = "SYSTEM",
    val dynamicColors: Boolean = true,
    val appFont: String = "DEFAULT",
)

@Composable
private fun RemoteMessageOverlay(
    remoteMessageManager: RemoteMessageManager,
    modifier: Modifier = Modifier,
) {
    val remoteMessage by remoteMessageManager.message.collectAsStateWithLifecycle()
    RemoteMessagePill(
        message = remoteMessage,
        onDismiss = remoteMessageManager::dismiss,
        modifier = modifier,
    )
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferencesRepository: PreferencesRepository

    @Inject lateinit var offlineModeManager: OfflineModeManager

    @Inject lateinit var audiobookshelfSocketManager: AudiobookshelfSocketManager

    @Inject lateinit var pendingNavigationManager: PendingNavigationManager

    @Inject lateinit var audioWidgetUpdater: AudioWidgetUpdater

    @Inject lateinit var remoteMessageManager: RemoteMessageManager

    private val authViewModel: AuthViewModel by viewModels()

    private val showNotificationRationale = MutableStateFlow(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                lifecycleScope.launch {
                    preferencesRepository.setNotificationPermissionDeclined(true)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audioWidgetUpdater.startIfWidgetsPlaced()

        setContent {
            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSize = calculateWindowSizeClass(this)
            val themePreferencesFlow = remember {
                combine(
                        preferencesRepository.getThemeModeFlow(),
                        preferencesRepository.getDynamicColorsFlow(),
                        preferencesRepository.getAppFontFlow(),
                    ) { themeMode, dynamicColors, appFont ->
                        ThemePreferences(
                            themeMode = themeMode,
                            dynamicColors = dynamicColors,
                            appFont = appFont,
                        )
                    }
                    .distinctUntilChanged()
            }
            val themePreferences by
                themePreferencesFlow.collectAsStateWithLifecycle(
                    initialValue = ThemePreferences(),
                )
            AFinityTheme(
                themeMode = themePreferences.themeMode,
                dynamicColor = themePreferences.dynamicColors,
                appFont = themePreferences.appFont,
            ) {
                val windowInsetsController =
                    remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
                val mode = ThemeMode.fromString(themePreferences.themeMode)
                val systemInDarkTheme = isSystemInDarkTheme()

                val isLightTheme =
                    when (mode) {
                        ThemeMode.SYSTEM -> !systemInDarkTheme
                        ThemeMode.LIGHT -> true
                        ThemeMode.DARK,
                        ThemeMode.AMOLED -> false
                    }

                val statusBarIcons = remember { StatusBarIconsState() }
                StatusBarIconsEffect(windowInsetsController, isLightTheme, statusBarIcons)

                SideEffect { windowInsetsController.isAppearanceLightNavigationBars = isLightTheme }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CompositionLocalProvider(LocalStatusBarIcons provides statusBarIcons) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainContent(
                                offlineModeManager = offlineModeManager,
                                widthSizeClass = windowSize.widthSizeClass,
                                modifier = Modifier.fillMaxSize(),
                                viewModel = authViewModel,
                            )

                            RemoteMessageOverlay(
                                remoteMessageManager = remoteMessageManager,
                                modifier =
                                    Modifier.align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(top = 8.dp),
                            )
                        }
                    }
                }

                val showRationale by showNotificationRationale.collectAsStateWithLifecycle()
                if (showRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            showNotificationRationale.value = false
                            lifecycleScope.launch {
                                preferencesRepository.setNotificationPermissionDeclined(true)
                            }
                        },
                        title = { Text(stringResource(R.string.notification_permission_title)) },
                        text = { Text(stringResource(R.string.notification_permission_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale.value = false
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.notification_permission_yes))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showNotificationRationale.value = false
                                    lifecycleScope.launch {
                                        preferencesRepository.setNotificationPermissionDeclined(
                                            true,
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.notification_permission_no))
                            }
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            val alreadyGranted =
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            val declined = preferencesRepository.getNotificationPermissionDeclined()
            if (!alreadyGranted && !declined) {
                showNotificationRationale.value = true
            }
        }

        handleNavigationExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigationExtras(intent)
    }

    private fun handleNavigationExtras(intent: Intent) {
        if (intent.getBooleanExtra(PendingNavigationManager.EXTRA_OPEN_DOWNLOADS, false)) {
            pendingNavigationManager.navigateTo(Destination.createDownloadSettingsRoute())
        }
        when (intent.getStringExtra(AudioWidgetIntents.EXTRA_OPEN_PLAYER)) {
            AudioWidgetIntents.PLAYER_MUSIC ->
                pendingNavigationManager.navigateTo(Destination.MUSIC_PLAYER_ROUTE)
            AudioWidgetIntents.PLAYER_ABS ->
                intent.getStringExtra(AudioWidgetIntents.EXTRA_ABS_ITEM_ID)?.let { itemId ->
                    pendingNavigationManager.navigateTo(
                        Destination.createAudiobookshelfPlayerRoute(
                            itemId = itemId,
                            episodeId =
                                intent.getStringExtra(AudioWidgetIntents.EXTRA_ABS_EPISODE_ID),
                        ),
                    )
                }
        }
        intent.removeExtra(AudioWidgetIntents.EXTRA_OPEN_PLAYER)
    }
}

@Composable
private fun MainContent(
    offlineModeManager: OfflineModeManager,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authenticationState.collectAsStateWithLifecycle()

    MainNavigation(
        offlineModeManager = offlineModeManager,
        widthSizeClass = widthSizeClass,
        authState = authState,
        modifier = modifier.fillMaxSize(),
    )
}
