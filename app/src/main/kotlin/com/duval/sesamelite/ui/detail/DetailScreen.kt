package com.duval.sesamelite.ui.detail

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duval.sesamelite.R
import com.duval.sesamelite.crypto.CryptoManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: DetailViewModel,
    entryId: String,
    onDismiss: () -> Unit,
    onEdit: (String) -> Unit,
    onShare: (String) -> Unit
) {
    LaunchedEffect(entryId) { vm.load(entryId) }

    val state by vm.state.collectAsState()
    val entry = state.entry
    val context = LocalContext.current

    // Capture nullable fields as local vals to enable smart casts
    val plainCode = state.plainCode
    val plainAddress = state.plainAddress
    val plainLocationDetails = state.plainLocationDetails
    val plainComment = state.plainComment

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                actions = {
                    IconButton(onClick = { entry?.let { onShare(it.id) } }) {
                        Icon(Icons.Default.QrCode, stringResource(R.string.qr_title))
                    }
                    IconButton(onClick = { entry?.let { onEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, null)
                    }
                }
            )
        }
    ) { padding ->
        if (entry == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Label
            DetailSectionCard(title = stringResource(R.string.label_header)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Code section
            DetailSectionCard(title = stringResource(R.string.code_header)) {
                val isFuture = entry.code?.let(CryptoManager::isFutureVersion) == true
                val hasCodeButUnavailable = entry.code != null && plainCode == null && !isFuture

                when {
                    isFuture -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFFF9500))
                            Text(stringResource(R.string.entry_requires_newer_version), color = Color(0xFFFF9500))
                        }
                    }
                    hasCodeButUnavailable -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.outline)
                            Text(stringResource(R.string.key_unavailable_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    plainCode != null -> {
                        val showCode = state.showCode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val displayText = if (showCode) plainCode else "•".repeat(plainCode.length)
                            Text(
                                displayText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row {
                                IconButton(onClick = vm::toggleShowCode) {
                                    Icon(
                                        if (showCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null
                                    )
                                }
                                if (showCode) {
                                    IconButton(onClick = {
                                        copyToClipboard(context, plainCode, isSensitive = true)
                                    }) {
                                        Icon(Icons.Default.ContentCopy, stringResource(R.string.action_copy))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.detail_no_code), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Address section
            if (!plainAddress.isNullOrEmpty()) {
                val isUnresolved = entry.encryptedLatitude == null && entry.encryptedLongitude == null
                DetailSectionCard(title = stringResource(R.string.address_header)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isUnresolved) Icons.Default.LocationOff else Icons.Default.LocationOn,
                            null,
                            tint = if (isUnresolved) Color(0xFFFF9500) else Color(0xFF34C759),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plainAddress)
                            if (isUnresolved) {
                                Text(
                                    stringResource(R.string.address_warning_message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF9500)
                                )
                            }
                        }
                        if (!isUnresolved) {
                            val lat = vm.repo.decryptedLatitude(entry)
                            val lon = vm.repo.decryptedLongitude(entry)
                            if (lat != null && lon != null) {
                                IconButton(onClick = {
                                    openInMaps(context, lat, lon, entry.label)
                                }) {
                                    Icon(Icons.Default.OpenInNew, null, tint = Color(0xFF34C759))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { copyToClipboard(context, plainAddress) }) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_copy))
                    }
                }
            }

            // Radius & silence
            DetailSectionCard(title = stringResource(R.string.radius_header)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
                        Text("${entry.radiusMeters.toInt()} m")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (entry.isSilenced) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            null,
                            tint = if (entry.isSilenced) MaterialTheme.colorScheme.outline else Color(0xFFFF9500)
                        )
                        Text(
                            stringResource(if (entry.isSilenced) R.string.detail_silenced else R.string.detail_active),
                            color = if (entry.isSilenced) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Location details
            if (!plainLocationDetails.isNullOrEmpty()) {
                DetailSectionCard(title = stringResource(R.string.location_details_header)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text(plainLocationDetails, modifier = Modifier.weight(1f))
                        IconButton(onClick = { copyToClipboard(context, plainLocationDetails) }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Comment
            if (!plainComment.isNullOrEmpty()) {
                DetailSectionCard(title = stringResource(R.string.comment_header)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text(plainComment, modifier = Modifier.weight(1f))
                        IconButton(onClick = { copyToClipboard(context, plainComment) }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, isSensitive: Boolean = false) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Sesame", text)
    if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    cm.setPrimaryClip(clip)
}

private fun openInMaps(context: Context, lat: Double, lon: Double, label: String) {
    val encoded = Uri.encode(label)
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($encoded)")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
