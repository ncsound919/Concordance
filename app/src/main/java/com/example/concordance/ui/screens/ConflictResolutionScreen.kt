package com.example.concordance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.concordance.integration.supabase.SyncConflict

@Composable
fun ConflictResolutionScreen(
    conflicts: List<SyncConflict>,
    onResolve: (SyncConflict, String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sync conflicts", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        if (conflicts.isEmpty()) {
            Text("No conflicts. Everything is in sync.")
            return
        }
        conflicts.forEach { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${c.entityType}: ${c.entityId}", style = MaterialTheme.typography.titleSmall)
                    Text(c.reason, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onResolve(c, "accept_agent") }) { Text("Accept agent proposal") }
                        OutlinedButton(onClick = { onResolve(c, "keep_local") }) { Text("Keep local") }
                        OutlinedButton(onClick = { onResolve(c, "keep_server") }) { Text("Keep server") }
                        OutlinedButton(onClick = { onResolve(c, "open_challenge") }) { Text("Open challenge") }
                    }
                }
            }
        }
    }
}