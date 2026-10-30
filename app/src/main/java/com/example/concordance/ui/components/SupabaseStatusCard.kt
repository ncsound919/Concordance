package com.example.concordance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.integration.supabase.SupabaseSyncManager
import com.example.concordance.integration.supabase.createSupabaseSessionStore
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun SupabaseStatusCard(
    onSyncNow: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { createSupabaseSessionStore(context) }
    val auth = remember { SupabaseAuth(store) }
    val session = auth.session
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Idle") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.auto_044c2076db4a), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (session == null) {
                Text(stringResource(R.string.auto_e239ecba166f), style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Signed in: ${session.email ?: session.userId}", style = MaterialTheme.typography.bodySmall)
                Text("Status: $status", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            status = "Syncing…"
                            onSyncNow()
                            status = "Done"
                        }
                    }) { Text(stringResource(R.string.auto_2b7d938e6787)) }
                }
            }
        }
    }
}
