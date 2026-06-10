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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownImportScreen(isFutureVersion: Boolean, onDismiss: () -> Unit) {
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
                if (isFutureVersion) Icons.Default.ArrowUpward else Icons.Default.Warning,
                null,
                modifier = Modifier.size(64.dp),
                tint = if (isFutureVersion) MaterialTheme.colorScheme.primary else Color(0xFFFF9500)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (isFutureVersion) R.string.import_future_version_title else R.string.import_malformed_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (isFutureVersion) R.string.import_future_version_message else R.string.import_malformed_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
