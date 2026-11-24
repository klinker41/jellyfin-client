package com.makd.afinity.tvlauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.tvlauncher.TvLauncherManager
import com.makd.afinity.tvlauncher.TvLauncherViewModel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.focusOutline
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun TvLauncherTopBarActions(
    modifier: Modifier = Modifier,
    viewModel: TvLauncherViewModel = hiltViewModel(),
) {
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var formattedTime by remember {
        mutableStateOf(DateFormat.getTimeFormat(context).format(Date()))
    }
    LaunchedEffect(context) {
        while (true) {
            formattedTime = DateFormat.getTimeFormat(context).format(Date())
            val nowMs = System.currentTimeMillis()
            val millisUntilNextMinute = (60_000L - (nowMs % 60_000L)).coerceAtLeast(1_000L)
            delay(millisUntilNextMinute)
        }
    }

    TvLauncherTopBarActionsContent(
        isNetworkAvailable = isNetworkAvailable,
        formattedTime = formattedTime,
        onOpenSystemSettings = { TvLauncherManager.openSystemSettings(context) },
        modifier = modifier,
    )
}

@Composable
fun TvLauncherTopBarActionsContent(
    isNetworkAvailable: Boolean,
    formattedTime: String,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier.size(36.dp).focusOutline(CircleShape),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.tv_launcher_cd_system_settings),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier.height(36.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        id =
                            if (isNetworkAvailable) R.drawable.ic_wifi else R.drawable.ic_cloud_off,
                    ),
                contentDescription =
                    stringResource(
                        if (isNetworkAvailable) R.string.tv_launcher_cd_network_connected
                        else R.string.tv_launcher_cd_network_disconnected,
                    ),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.width(CardDimensions.RowGutter - 4.dp))
    }
}
