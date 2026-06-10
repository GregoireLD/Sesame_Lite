package com.paris.duval.sesamelite.ui.share

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.paris.duval.sesamelite.prefs.AppPrefs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.paris.duval.sesamelite.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRShareScreen(
    vm: QRShareViewModel,
    entryId: String,
    onDismiss: () -> Unit
) {
    LaunchedEffect(entryId) { vm.load(entryId) }

    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val showHidden by AppPrefs.showHiddenFeatures

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_title)) },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Entry label
            if (state.label.isNotEmpty()) {
                Text(state.label, style = MaterialTheme.typography.titleMedium)
            }

            // QR code display
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val qrBitmap = state.qrBitmap
                when {
                    qrBitmap != null -> {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_title),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    state.generationFailed -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.qr_generation_failed), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> CircularProgressIndicator()
                }
            }

            // Options
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.qr_options_header).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        SwitchRow(
                            label = stringResource(R.string.qr_option_radius),
                            checked = state.includeRadius,
                            onCheckedChange = vm::setIncludeRadius,
                            icon = Icons.Default.LocationOn
                        )
                        SwitchRow(
                            label = stringResource(R.string.qr_option_location_details),
                            checked = state.includeLocationDetails,
                            onCheckedChange = vm::setIncludeLocationDetails,
                            icon = Icons.Default.Info,
                            iconTint = Color(0xFFFF9500)
                        )
                        SwitchRow(
                            label = stringResource(R.string.qr_option_comment),
                            checked = state.includeComment,
                            onCheckedChange = vm::setIncludeComment,
                            icon = Icons.AutoMirrored.Filled.Comment,
                            iconTint = MaterialTheme.colorScheme.tertiary
                        )
                        if (showHidden) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            SwitchRow(
                                label = stringResource(R.string.qr_option_exclude_coordinates),
                                checked = !state.includeCoordinates,
                                onCheckedChange = { vm.setIncludeCoordinates(!it) },
                                icon = Icons.Default.LocationOff,
                                iconTint = Color(0xFF9B59B6)
                            )
                            SwitchRow(
                                label = stringResource(R.string.qr_option_legacy_scheme),
                                checked = state.useLegacyScheme,
                                onCheckedChange = vm::setUseLegacyScheme,
                                icon = Icons.Default.Link,
                                iconTint = Color(0xFF9B59B6)
                            )
                        }
                    }
                }
            }

            // Warning
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Shield, null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.qr_warning), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Share button
            Button(
                onClick = {
                    val url = state.shareUrl ?: return@Button
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
                enabled = state.shareUrl != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.qr_share))
            }

            Text(
                stringResource(R.string.qr_share_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = iconTint)
            Text(label)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
