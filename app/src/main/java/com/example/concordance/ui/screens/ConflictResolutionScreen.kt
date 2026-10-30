package com.example.concordance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.concordance.integration.supabase.SyncConflict
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun ConflictResolutionScreen(
    conflicts: List<SyncConflict>,
    onResolve: (SyncConflict, String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.auto_39cc48268cdb), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (conflicts.isEmpty()) {
            Text(stringResource(R.string.auto_08dbbbd1a4df))
            return
        }
        conflicts.forEach { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${c.entityType}: ${c.entityId}", style = MaterialTheme.typography.titleSmall)
                    Text(c.reason, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onResolve(c, "accept_agent") }) { Text(stringResource(R.string.auto_aecc26ff1fcb)) }
                        OutlinedButton(onClick = { onResolve(c, "keep_local") }) { Text(stringResource(R.string.auto_a94cb137c24a)) }
                        OutlinedButton(onClick = { onResolve(c, "keep_server") }) { Text(stringResource(R.string.auto_9b6cfc1c2e1f)) }
                        OutlinedButton(onClick = { onResolve(c, "open_challenge") }) { Text(stringResource(R.string.auto_44e1a3efdc85)) }
                    }
                }
            }
        }
    }
}
