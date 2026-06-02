package com.duval.sesamelite.ui.addedit

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.duval.sesamelite.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    vm: AddEditViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = onDismiss
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.saved) {
        if (state.saved) onDismiss()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    // Dialogs
    if (state.keyUnavailable) {
        AlertDialog(
            onDismissRequest = vm::dismissKeyUnavailable,
            title = { Text(stringResource(R.string.error_key_unavailable_title)) },
            text = { Text(stringResource(R.string.error_key_unavailable_message)) },
            confirmButton = { TextButton(onClick = vm::dismissKeyUnavailable) { Text(stringResource(R.string.action_ok)) } }
        )
    }

    if (state.showUnresolvedWarning) {
        AlertDialog(
            onDismissRequest = vm::dismissUnresolvedWarning,
            title = { Text(stringResource(R.string.address_warning_title)) },
            text = { Text(stringResource(R.string.address_warning_message)) },
            confirmButton = { TextButton(onClick = vm::forceSave) { Text(stringResource(R.string.address_warning_save_anyway)) } },
            dismissButton = { TextButton(onClick = vm::dismissUnresolvedWarning) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (state.showClipboardError) {
        AlertDialog(
            onDismissRequest = vm::dismissClipboardError,
            title = { Text(stringResource(R.string.clipboard_error_title)) },
            text = { Text(stringResource(R.string.clipboard_error_message)) },
            confirmButton = { TextButton(onClick = vm::dismissClipboardError) { Text(stringResource(R.string.action_ok)) } }
        )
    }

    if (state.showClipboardOverwrite) {
        AlertDialog(
            onDismissRequest = vm::dismissClipboardOverwrite,
            title = { Text(stringResource(R.string.clipboard_overwrite_title)) },
            text = { Text(stringResource(R.string.clipboard_overwrite_message)) },
            confirmButton = { TextButton(onClick = vm::confirmClipboardOverwrite) { Text(stringResource(R.string.clipboard_overwrite_confirm)) } },
            dismissButton = { TextButton(onClick = vm::dismissClipboardOverwrite) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = { TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = vm::dismissDelete) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (state.showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = vm::dismissDuplicateWarning,
            title = { Text(stringResource(R.string.duplicate_warning_title)) },
            text = { Text(stringResource(R.string.duplicate_warning_message)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = vm::confirmReplaceMatch) {
                        Text(stringResource(R.string.duplicate_replace))
                    }
                    TextButton(onClick = vm::confirmSaveAnyway) {
                        Text(stringResource(R.string.duplicate_create_anyway))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDuplicateWarning) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state.isEditing -> R.string.edit_title
                                state.isImporting -> R.string.import_title
                                else -> R.string.add_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                actions = {
                    if (!state.isImporting) {
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
                            vm.importFromClipboard(text)
                        }) {
                            Icon(Icons.Default.ContentPaste, stringResource(R.string.clipboard_import))
                        }
                    }
                    TextButton(
                        onClick = vm::attemptSave,
                        enabled = state.canSave
                    ) {
                        Text(stringResource(R.string.action_done))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Label
            SectionCard(title = stringResource(R.string.label_header)) {
                OutlinedTextField(
                    value = state.label,
                    onValueChange = vm::onLabelChange,
                    label = { Text(stringResource(R.string.label_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(stringResource(R.string.label_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Code
            SectionCard(title = stringResource(R.string.code_header)) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = vm::onCodeChange,
                    label = { Text(stringResource(R.string.code_placeholder)) },
                    visualTransformation = if (state.showCode) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = vm::onShowCodeToggle) {
                            Icon(
                                if (state.showCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(stringResource(R.string.code_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Address
            SectionCard(title = stringResource(R.string.address_header)) {
                OutlinedTextField(
                    value = state.address,
                    onValueChange = vm::onAddressEditedByUser,
                    label = { Text(stringResource(R.string.address_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // Look-up button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (state.geoState) {
                        GeoState.Geocoding -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.address_looking_up))
                        }
                        GeoState.Resolved -> {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.address_found), color = Color(0xFF34C759))
                        }
                        else -> {
                            TextButton(
                                onClick = { vm.geocodeAddress() },
                                enabled = state.address.isNotEmpty() && state.geoState != GeoState.Geocoding
                            ) {
                                Icon(Icons.Default.Search, null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.address_look_up))
                            }
                        }
                    }
                }
                state.geoError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.geoState == GeoState.Resolved && state.latitude != null && state.longitude != null) {
                    Text(
                        "%.5f, %.5f".format(state.latitude, state.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(stringResource(R.string.address_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Radius & Silence
            SectionCard(title = stringResource(R.string.radius_header)) {
                Text("${state.radiusMeters.toInt()} m", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = state.radiusMeters.toFloat(),
                        onValueChange = { vm.onRadiusChange(it.toDouble()) },
                        valueRange = 50f..500f,
                        steps = 44,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = vm::onSilencedToggle) {
                        Icon(
                            if (state.isSilenced) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            null,
                            tint = if (state.isSilenced) MaterialTheme.colorScheme.outline else Color(0xFFFF9500)
                        )
                    }
                }
                Text(stringResource(R.string.radius_footer, "100 m"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Location details
            SectionCard(title = stringResource(R.string.location_details_header)) {
                OutlinedTextField(
                    value = state.locationDetails,
                    onValueChange = vm::onLocationDetailsChange,
                    label = { Text(stringResource(R.string.location_details_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
                Text(stringResource(R.string.location_details_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Comment
            SectionCard(title = stringResource(R.string.comment_header)) {
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = vm::onCommentChange,
                    label = { Text(stringResource(R.string.comment_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6
                )
                Text(stringResource(R.string.comment_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Delete (edit only)
            if (state.isEditing) {
                TextButton(
                    onClick = vm::requestDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}
