package com.paris.duval.sesamelite.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paris.duval.sesamelite.R

enum class ImportErrorKind { MALFORMED, FUTURE_VERSION, EMPTY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownImportScreen(kind: ImportErrorKind, onDismiss: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                when (kind) {
                    ImportErrorKind.FUTURE_VERSION -> Icons.Default.ArrowUpward
                    ImportErrorKind.EMPTY -> Icons.Default.LinkOff
                    ImportErrorKind.MALFORMED -> Icons.Default.Warning
                },
                null,
                modifier = Modifier.size(64.dp),
                tint = when (kind) {
                    ImportErrorKind.FUTURE_VERSION -> MaterialTheme.colorScheme.primary
                    ImportErrorKind.EMPTY -> MaterialTheme.colorScheme.secondary
                    ImportErrorKind.MALFORMED -> Color(0xFFFF9500)
                }
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(
                    when (kind) {
                        ImportErrorKind.FUTURE_VERSION -> R.string.import_future_version_title
                        ImportErrorKind.EMPTY -> R.string.import_empty_title
                        ImportErrorKind.MALFORMED -> R.string.import_malformed_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    when (kind) {
                        ImportErrorKind.FUTURE_VERSION -> R.string.import_future_version_message
                        ImportErrorKind.EMPTY -> R.string.import_empty_message
                        ImportErrorKind.MALFORMED -> R.string.import_malformed_message
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
