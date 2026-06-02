package com.duval.sesamelite.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duval.sesamelite.BuildConfig
import com.duval.sesamelite.R
import com.duval.sesamelite.prefs.AppPrefs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onDismiss: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onResetAllData: () -> Unit,
    onResetPermissionBanners: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val showHidden by AppPrefs.showHiddenFeatures

    var showSimulateAlert by remember { mutableStateOf(false) }
    var isPressing by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf<Int?>(null) }

    // Hold-to-simulate countdown (mirrors iOS's 10-second hold)
    LaunchedEffect(isPressing) {
        if (isPressing) {
            for (i in 10 downTo 0) {
                if (!isPressing) { countdown = null; return@LaunchedEffect }
                countdown = i
                if (i == 0) break
                delay(1000)
            }
            if (isPressing) {
                countdown = null
                showSimulateAlert = true
            }
        } else {
            countdown = null
        }
    }

    if (showSimulateAlert) {
        AlertDialog(
            onDismissRequest = { showSimulateAlert = false },
            title = { Text(stringResource(R.string.key_recovery_reset_confirm_title)) },
            text = { Text(stringResource(R.string.key_recovery_reset_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSimulateAlert = false
                    onResetAllData()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.key_recovery_reset_confirm_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimulateAlert = false; isPressing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconComposable()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Description ───────────────────────────────────────────────────
            AboutCard {
                Text(
                    stringResource(R.string.about_description),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Links ─────────────────────────────────────────────────────────
            AboutCard {
                LinkRow("sesame-app.com", "https://sesame-app.com", Icons.Default.Language, Color(0xFF1A73E8))
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                LinkRow(stringResource(R.string.about_tip), "https://ko-fi.com/duvalparis", Icons.Default.Favorite, Color(0xFFE91E63))
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                ClickableRow(
                    label = stringResource(R.string.about_reset_permissions),
                    icon = Icons.Default.PanTool,
                    iconTint = Color(0xFFFFB300),
                    onClick = {
                        onResetPermissionBanners()
                        onDismiss()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                ClickableRow(
                    label = stringResource(R.string.about_open_settings),
                    icon = Icons.Default.Settings,
                    iconTint = Color.Gray,
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Privacy ───────────────────────────────────────────────────────
            SectionHeader(stringResource(R.string.about_privacy_header))
            Spacer(Modifier.height(6.dp))
            AboutCard {
                LinkRow(stringResource(R.string.about_privacy_policy), "https://sesame-app.com/privacy.php", Icons.Default.Lock, Color(0xFF34C759))
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp).padding(top = 1.dp))
                    Text(
                        stringResource(R.string.about_privacy_statement),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Version (with easter egg long-press) ──────────────────────────
            AboutCard {
                // Version row — long-press toggles showHiddenFeatures
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    AppPrefs.toggleHiddenFeatures()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.about_version))
                    Text(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Hidden features section
                if (showHidden) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    ClickableRow(
                        label = stringResource(R.string.about_replay_onboarding),
                        icon = Icons.Default.Refresh,
                        iconTint = Color(0xFF9B59B6),
                        onClick = onReplayOnboarding
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                    // Reset All Data — hold for 10 s to confirm
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressing = true
                                        tryAwaitRelease()
                                        isPressing = false
                                    }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        val holdLabel = countdown?.let { "$it" } ?: stringResource(R.string.key_recovery_reset)
                        Text(holdLabel, color = MaterialTheme.colorScheme.error, modifier = Modifier.animateContentSize())
                    }
                }
            }

            // Credits footer
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.about_credits),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── App icon ──────────────────────────────────────────────────────────────────

@Composable
private fun AppIconComposable() {
    // AppIconImage.png already has pre-applied rounded corners (RGBA from iOS assets)
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.app_icon),
        contentDescription = null,
        modifier = Modifier.size(80.dp)
    )
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun AboutCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun LinkRow(
    label: String,
    url: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    ClickableRow(label = label, icon = icon, iconTint = iconTint, onClick = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    })
}

@Composable
private fun ClickableRow(
    label: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = textColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
    }
}
