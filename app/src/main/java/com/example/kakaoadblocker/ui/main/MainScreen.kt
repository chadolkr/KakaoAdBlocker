package com.example.kakaoadblocker.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kakaoadblocker.data.BlockedNotification
import com.example.kakaoadblocker.data.CleanupStatus
import com.example.kakaoadblocker.data.SharedPreferencesBlockLogRepository
import com.example.kakaoadblocker.theme.KakaoAdBlockerTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import com.example.kakaoadblocker.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(SharedPreferencesBlockLogRepository(appCtx))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check permission on screen resume (e.g. when returning from system settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkServicePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAccessibilityGuide by remember { mutableStateOf(false) }

    val openAccessibilitySettings = {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    if (showAccessibilityGuide) {
        AccessibilityGuideDialog(
            onOpenAccessibilitySettings = openAccessibilitySettings,
            onOpenAppInfo = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            },
            onDismiss = { showAccessibilityGuide = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F14) // Deep premium dark background
    ) {
        when (state) {
            MainScreenUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFEB3B))
                }
            }
            is MainScreenUiState.Success -> {
                val successState = state as MainScreenUiState.Success
                MainContent(
                    isServiceEnabled = successState.isServiceEnabled,
                    isBlockingEnabled = successState.isBlockingEnabled,
                    blockedLogs = successState.blockedLogs,
                    totalBlockedCount = successState.totalBlockedCount,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    onToggleBlocking = { enabled -> viewModel.setBlockingEnabled(enabled) },
                    isAccessibilityEnabled = successState.isAccessibilityEnabled,
                    isAutoBlockChannelEnabled = successState.isAutoBlockChannelEnabled,
                    isAutoLeaveChatEnabled = successState.isAutoLeaveChatEnabled,
                    onOpenAccessibilitySettings = {
                        // Android 13+ blocks accessibility for sideloaded apps until "restricted settings" are allowed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            showAccessibilityGuide = true
                        } else {
                            openAccessibilitySettings()
                        }
                    },
                    onStartScan = {
                        val message = if (viewModel.requestScan()) {
                            R.string.scan_started
                        } else {
                            R.string.scan_service_not_running
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    onToggleAutoBlockChannel = { enabled -> viewModel.setAutoBlockChannelEnabled(enabled) },
                    onToggleAutoLeaveChat = { enabled -> viewModel.setAutoLeaveChatEnabled(enabled) },
                    onRemoveLog = { id -> viewModel.removeLog(id) },
                    onClearAllLogs = { viewModel.clearAllLogs() },
                    modifier = modifier
                )
            }
            is MainScreenUiState.Error -> {
                val errorMsg = (state as MainScreenUiState.Error).throwable.message ?: ""
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.error_format, errorMsg),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainContent(
    isServiceEnabled: Boolean,
    isBlockingEnabled: Boolean,
    blockedLogs: List<BlockedNotification>,
    totalBlockedCount: Int,
    onOpenSettings: () -> Unit,
    onToggleBlocking: (Boolean) -> Unit,
    onRemoveLog: (String) -> Unit,
    onClearAllLogs: () -> Unit,
    modifier: Modifier = Modifier,
    isAccessibilityEnabled: Boolean = false,
    isAutoBlockChannelEnabled: Boolean = false,
    isAutoLeaveChatEnabled: Boolean = false,
    onOpenAccessibilitySettings: () -> Unit = {},
    onToggleAutoBlockChannel: (Boolean) -> Unit = {},
    onToggleAutoLeaveChat: (Boolean) -> Unit = {},
    onStartScan: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(id = R.string.app_subtitle),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            if (blockedLogs.isNotEmpty()) {
                IconButton(
                    onClick = onClearAllLogs,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete_all_desc)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Card
        StatusCard(
            isEnabled = isServiceEnabled,
            isBlockingEnabled = isBlockingEnabled,
            totalBlocked = totalBlockedCount,
            onOpenSettings = onOpenSettings,
            onToggleBlocking = onToggleBlocking
        )

        if (isServiceEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            CleanupCard(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isAutoBlockChannelEnabled = isAutoBlockChannelEnabled,
                isAutoLeaveChatEnabled = isAutoLeaveChatEnabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onToggleAutoBlockChannel = onToggleAutoBlockChannel,
                onToggleAutoLeaveChat = onToggleAutoLeaveChat,
                onStartScan = onStartScan
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // History Title
        Text(
            text = stringResource(id = R.string.history_title, blockedLogs.size),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Blocked Logs List
        if (blockedLogs.isEmpty()) {
            EmptyLogsView(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(blockedLogs, key = { it.id }) { log ->
                    LogCard(log = log, onRemove = { onRemoveLog(log.id) })
                }
            }
        }

        // Footer (Version & Developer info)
        val context = LocalContext.current
        val versionName = remember(context) {
            try {
                val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                packageInfo.versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.version_footer_format, versionName),
                fontSize = 11.sp,
                color = Color.Gray.copy(alpha = 0.6f),
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(6.dp))
            val privacyUrl = stringResource(id = R.string.privacy_policy_url)
            Text(
                text = stringResource(id = R.string.privacy_policy_title),
                fontSize = 11.sp,
                color = Color(0xFFFFEB3B).copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback/ignore if no activity can handle web browser intent
                    }
                }
            )
        }
    }
}

@Composable
fun StatusCard(
    isEnabled: Boolean,         // System Permission Enabled
    isBlockingEnabled: Boolean, // User Switch Enabled
    totalBlocked: Int,
    onOpenSettings: () -> Unit,
    onToggleBlocking: (Boolean) -> Unit
) {
    val gradientColors = if (!isEnabled) {
        listOf(Color(0xFFC62828), Color(0xFFD32F2F)) // Rose/Red gradient (No Permission)
    } else if (isBlockingEnabled) {
        listOf(Color(0xFF2E7D32), Color(0xFF0F9D58)) // Emerald/Green gradient (Active)
    } else {
        listOf(Color(0xFF37474F), Color(0xFF263238)) // Slate/Dark gray gradient (Paused)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(colors = gradientColors),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (!isEnabled) {
                            Icons.Default.Warning
                        } else if (isBlockingEnabled) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (!isEnabled) {
                                stringResource(id = R.string.title_service_disabled)
                            } else if (isBlockingEnabled) {
                                stringResource(id = R.string.title_service_enabled)
                            } else {
                                stringResource(id = R.string.title_service_paused)
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (!isEnabled) {
                                stringResource(id = R.string.desc_service_disabled)
                            } else if (isBlockingEnabled) {
                                stringResource(id = R.string.desc_service_enabled)
                            } else {
                                stringResource(id = R.string.desc_service_paused)
                            },
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                if (isEnabled) {
                    Switch(
                        checked = isBlockingEnabled,
                        onCheckedChange = onToggleBlocking,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFEB3B),
                            checkedTrackColor = Color(0xFFFFEB3B).copy(alpha = 0.5f),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats row or Permission button
            if (isEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.stats_title),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(id = R.string.stats_count_format, totalBlocked),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    if (isBlockingEnabled) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.badge_monitoring),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.badge_waiting),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.button_grant_permission),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CleanupCard(
    isAccessibilityEnabled: Boolean,
    isAutoBlockChannelEnabled: Boolean,
    isAutoLeaveChatEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleAutoBlockChannel: (Boolean) -> Unit,
    onToggleAutoLeaveChat: (Boolean) -> Unit,
    onStartScan: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C24)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.cleanup_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (isAccessibilityEnabled) {
                    stringResource(id = R.string.cleanup_desc_enabled)
                } else {
                    stringResource(id = R.string.cleanup_desc_disabled)
                },
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (isAccessibilityEnabled) {
                CleanupSwitchRow(
                    label = stringResource(id = R.string.cleanup_auto_block),
                    checked = isAutoBlockChannelEnabled,
                    onCheckedChange = onToggleAutoBlockChannel
                )
                CleanupSwitchRow(
                    label = stringResource(id = R.string.cleanup_auto_leave),
                    checked = isAutoLeaveChatEnabled,
                    onCheckedChange = onToggleAutoLeaveChat
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onStartScan,
                    enabled = isAutoBlockChannelEnabled || isAutoLeaveChatEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFEB3B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.button_scan_existing),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = stringResource(id = R.string.scan_desc),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEB3B), contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.button_grant_accessibility),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AccessibilityGuideDialog(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C24),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE0E0E0),
        title = { Text(text = stringResource(id = R.string.guide_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.guide_intro), fontSize = 13.sp, color = Color.Gray)
                GuideStep(
                    number = 1,
                    text = stringResource(id = R.string.guide_step1),
                    buttonLabel = stringResource(id = R.string.guide_open_accessibility),
                    onClick = onOpenAccessibilitySettings
                )
                GuideStep(
                    number = 2,
                    text = stringResource(id = R.string.guide_step2),
                    buttonLabel = stringResource(id = R.string.guide_open_app_info),
                    onClick = onOpenAppInfo
                )
                GuideStep(
                    number = 3,
                    text = stringResource(id = R.string.guide_step3),
                    buttonLabel = stringResource(id = R.string.guide_open_accessibility),
                    onClick = onOpenAccessibilitySettings
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.guide_close), color = Color(0xFFFFEB3B))
            }
        }
    )
}

@Composable
private fun GuideStep(
    number: Int,
    text: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Column {
        Row {
            Text(
                text = "$number.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFEB3B),
                modifier = Modifier.width(20.dp)
            )
            Text(text = text, fontSize = 14.sp, color = Color(0xFFE0E0E0))
        }
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFEB3B)),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.padding(start = 20.dp, top = 4.dp)
        ) {
            Text(text = buttonLabel, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CleanupSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFFE0E0E0),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFEB3B),
                checkedTrackColor = Color(0xFFFFEB3B).copy(alpha = 0.5f),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun CleanupStatusChip(status: CleanupStatus) {
    val (labelRes, color) = when (status) {
        CleanupStatus.NONE -> return
        CleanupStatus.PENDING -> R.string.cleanup_status_pending to Color(0xFFFFEB3B)
        CleanupStatus.DONE -> R.string.cleanup_status_done to Color(0xFF66BB6A)
        CleanupStatus.PARTIAL -> R.string.cleanup_status_partial to Color(0xFFFFA726)
        CleanupStatus.NOT_CHANNEL -> R.string.cleanup_status_not_channel to Color.Gray
        CleanupStatus.FAILED -> R.string.cleanup_status_failed to Color(0xFFFF5252)
    }
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = stringResource(id = labelRes),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun LogCard(
    log: BlockedNotification,
    onRemove: () -> Unit
) {
    val formatter = remember {
        val pattern = if (Locale.getDefault().language == "ko") "MM월 dd일 HH:mm" else "MMM dd, HH:mm"
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    val timeString = remember(log.timestamp) { formatter.format(Date(log.timestamp)) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C24)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.sender,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeString,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.text,
                    fontSize = 13.sp,
                    color = Color(0xFFE0E0E0),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CleanupStatusChip(status = log.cleanupStatus)
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Gray)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.delete_desc),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyLogsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF303040),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.empty_logs_title),
            fontSize = 15.sp,
            color = Color.Gray
        )
        Text(
            text = stringResource(id = R.string.empty_logs_desc),
            fontSize = 12.sp,
            color = Color(0xFF404050),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KakaoAdBlockerTheme {
        MainContent(
            isServiceEnabled = true,
            isBlockingEnabled = true,
            blockedLogs = listOf(
                BlockedNotification(sender = "쿠팡", text = "(광고) 골드박스 단 하루 특가 상품 확인해보세요!", timestamp = System.currentTimeMillis(), cleanupStatus = CleanupStatus.DONE),
                BlockedNotification(sender = "배달의민족", text = "(광고) 오늘 저녁은 치킨 어때요? 5,000원 쿠폰 지급!", timestamp = System.currentTimeMillis() - 600000)
            ),
            totalBlockedCount = 2,
            onOpenSettings = {},
            onToggleBlocking = {},
            onRemoveLog = {},
            onClearAllLogs = {},
            isAccessibilityEnabled = true,
            isAutoBlockChannelEnabled = true,
            isAutoLeaveChatEnabled = true
        )
    }
}



