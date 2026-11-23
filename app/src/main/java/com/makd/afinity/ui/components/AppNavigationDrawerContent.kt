package com.makd.afinity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.makd.afinity.BuildConfig
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.settings.ServerSessionGroupItem
import com.makd.afinity.ui.settings.SessionSwitcherState
import com.makd.afinity.ui.settings.SessionSwitcherViewModel
import com.makd.afinity.ui.settings.UserSession
import java.util.UUID

@Composable
fun AppNavigationDrawerContent(
    currentDestination: NavDestination?,
    userName: String?,
    userProfileImageUrl: String?,
    serverName: String?,
    connectionType: ConnectionType,
    isAdmin: Boolean,
    isOffline: Boolean,
    favoritesCount: Int,
    watchlistCount: Int,
    isJellyseerrAuthenticated: Boolean,
    isAudiobookshelfAuthenticated: Boolean,
    hasLiveTvAccess: Boolean,
    librariesInDrawer: Boolean,
    libraries: List<AfinityCollection>,
    selectedLibraryId: String?,
    onDestinationClick: (Destination) -> Unit,
    onLibraryClick: (AfinityCollection) -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccountClick: (Server) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    sessionSwitcherViewModel: SessionSwitcherViewModel = hiltViewModel(),
) {
    val sessionState by sessionSwitcherViewModel.state.collectAsStateWithLifecycle()
    AppNavigationDrawerContent(
        currentDestination = currentDestination,
        userName = userName,
        userProfileImageUrl = userProfileImageUrl,
        serverName = serverName,
        connectionType = connectionType,
        isAdmin = isAdmin,
        isOffline = isOffline,
        favoritesCount = favoritesCount,
        watchlistCount = watchlistCount,
        isJellyseerrAuthenticated = isJellyseerrAuthenticated,
        isAudiobookshelfAuthenticated = isAudiobookshelfAuthenticated,
        hasLiveTvAccess = hasLiveTvAccess,
        librariesInDrawer = librariesInDrawer,
        libraries = libraries,
        selectedLibraryId = selectedLibraryId,
        sessionState = sessionState,
        onDestinationClick = onDestinationClick,
        onLibraryClick = onLibraryClick,
        onSettingsClick = onSettingsClick,
        onAddAccountClick = onAddAccountClick,
        onSwitchSession = sessionSwitcherViewModel::switchSession,
        onForgetSession = sessionSwitcherViewModel::forgetSession,
        onCloseDrawer = onCloseDrawer,
        modifier = modifier,
        isPinned = isPinned,
    )
}

@Composable
fun AppNavigationDrawerContent(
    currentDestination: NavDestination?,
    userName: String?,
    userProfileImageUrl: String?,
    serverName: String?,
    connectionType: ConnectionType,
    isAdmin: Boolean,
    isOffline: Boolean,
    favoritesCount: Int,
    watchlistCount: Int,
    isJellyseerrAuthenticated: Boolean,
    isAudiobookshelfAuthenticated: Boolean,
    hasLiveTvAccess: Boolean,
    librariesInDrawer: Boolean,
    libraries: List<AfinityCollection>,
    selectedLibraryId: String?,
    sessionState: SessionSwitcherState,
    onDestinationClick: (Destination) -> Unit,
    onLibraryClick: (AfinityCollection) -> Unit,
    onSettingsClick: () -> Unit,
    onAddAccountClick: (Server) -> Unit,
    onSwitchSession: (serverId: String, userId: UUID) -> Unit,
    onForgetSession: (UserSession) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    appVersionName: String = BuildConfig.VERSION_NAME,
) {
    var accountSwitcherExpanded by remember { mutableStateOf(false) }
    var sessionToForget by remember { mutableStateOf<UserSession?>(null) }

    sessionToForget?.let { session ->
        ForgetAccountDialog(
            userName = session.username,
            onConfirm = { onForgetSession(session) },
            onDismiss = { sessionToForget = null },
        )
    }
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (accountSwitcherExpanded) 180f else 0f,
            label = "chevron",
        )

    val drawerItemColors =
        if (isPinned) {
            NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent,
                selectedContainerColor =
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.80f),
            )
        } else {
            NavigationDrawerItemDefaults.colors()
        }
    val itemHorizontalPadding = if (isPinned) 10.dp else 12.dp
    val cutoutModifier =
        if (isPinned) {
            Modifier
        } else {
            Modifier.windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            )
        }

    Box(
        modifier =
            if (isPinned) {
                modifier.fillMaxSize()
            } else {
                modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(if (isPinned) Modifier else Modifier.statusBarsPadding())
                        .then(cutoutModifier)
                        .padding(bottom = if (isPinned) 12.dp else 16.dp),
            ) {
                Spacer(modifier = Modifier.height(if (isPinned) 12.dp else 16.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = if (isPinned) 12.dp else 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) {
                                accountSwitcherExpanded = !accountSwitcherExpanded
                            }
                            .padding(
                                horizontal = if (isPinned) 6.dp else 8.dp,
                                vertical = 8.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isPinned) 10.dp else 12.dp),
                ) {
                    UserAvatar(
                        imageUrl = userProfileImageUrl,
                        name = userName,
                        size = if (isPinned) 40.dp else 48.dp,
                        contentDescription = stringResource(R.string.cd_profile_icon),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = userName ?: stringResource(R.string.app_name),
                                style =
                                    if (isPinned) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )

                            if (isAdmin) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.drawer_admin_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier =
                                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(8.dp)
                                        .background(
                                            connectionIndicatorColor(connectionType),
                                            CircleShape,
                                        ),
                            )
                            Text(
                                text =
                                    "${serverName ?: stringResource(R.string.app_name)} · " +
                                        connectionLabel(connectionType),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription = stringResource(R.string.cd_switch_account),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(chevronRotation),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                AnimatedVisibility(
                    visible = accountSwitcherExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column(
                        modifier =
                            Modifier.fillMaxWidth().then(cutoutModifier).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        sessionState.sessionGroups.forEach { sessionGroup ->
                            ServerSessionGroupItem(
                                sessionGroup = sessionGroup,
                                onSessionClick = { session ->
                                    onSwitchSession(session.serverId, session.userId)
                                    accountSwitcherExpanded = false
                                    onCloseDrawer()
                                },
                                onSessionLongClick = { session -> sessionToForget = session },
                                onAddAccountClick = { onAddAccountClick(sessionGroup.server) },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().then(cutoutModifier),
                ) {
                    Destination.entries.forEach { destination ->
                        if (destination == Destination.LIBRARIES && !isOffline) {
                            libraries.forEach { library ->
                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            text = library.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    selected = library.id.toString() == selectedLibraryId,
                                    onClick = { onLibraryClick(library) },
                                    icon = {
                                        Icon(
                                            painter = libraryTypeIcon(library.type),
                                            contentDescription = library.name,
                                        )
                                    },
                                    colors = drawerItemColors,
                                    modifier =
                                        Modifier.padding(
                                            horizontal = itemHorizontalPadding,
                                            vertical = 2.dp,
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                )
                            }
                        }

                        val visible =
                            when {
                                isOffline && destination != Destination.HOME -> false
                                destination == Destination.LIBRARIES -> librariesInDrawer
                                destination == Destination.FAVORITES -> favoritesCount > 0
                                destination == Destination.WATCHLIST -> watchlistCount > 0
                                destination == Destination.REQUESTS -> isJellyseerrAuthenticated
                                destination == Destination.AUDIOBOOKS ->
                                    isAudiobookshelfAuthenticated
                                destination == Destination.LIVE_TV -> hasLiveTvAccess
                                else -> true
                            }
                        if (!visible) return@forEach

                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } ==
                                true
                        val badgeCount =
                            when (destination) {
                                Destination.FAVORITES -> favoritesCount
                                Destination.WATCHLIST -> watchlistCount
                                else -> 0
                            }

                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = stringResource(destination.titleRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            selected = selected,
                            onClick = { onDestinationClick(destination) },
                            icon = {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (selected) destination.selectedIconRes
                                                else destination.unselectedIconRes,
                                        ),
                                    contentDescription = stringResource(destination.titleRes),
                                )
                            },
                            badge = {
                                if (badgeCount > 0) {
                                    Text(
                                        text = badgeCount.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            },
                            colors = drawerItemColors,
                            modifier =
                                Modifier.padding(
                                    horizontal = itemHorizontalPadding,
                                    vertical = 2.dp,
                                ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        selected = currentDestination?.route == Destination.SETTINGS_ROUTE,
                        onClick = onSettingsClick,
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        },
                        colors = drawerItemColors,
                        modifier =
                            Modifier.padding(
                                horizontal = itemHorizontalPadding,
                                vertical = 2.dp,
                            ),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            Text(
                text = stringResource(R.string.drawer_app_version, appVersionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    if (isPinned) {
                        Modifier.padding(start = 20.dp, top = 12.dp, bottom = 14.dp)
                    } else {
                        Modifier.then(cutoutModifier)
                            .navigationBarsPadding()
                            .padding(start = 24.dp, top = 16.dp, bottom = 16.dp)
                    },
            )
        }
    }
}
