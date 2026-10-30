package com.example.concordance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.concordance.model.Anchor
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.EmptyState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun ClaimsScreen(
    viewModel: ConcordanceViewModel,
    onOpenClaim: (Anchor) -> Unit,
    modifier: Modifier = Modifier
) {
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filtered = remember(claims, query) {
        if (query.isBlank()) claims else claims.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.source.contains(query, ignoreCase = true)
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.auto_cad5f3de36b9)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true
            )
            if (filtered.isEmpty()) {
                EmptyState(
                    title = "No claims",
                    message = "Register a claim to start the ledger.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { claim ->
                        ListItem(
                            headlineContent = { Text(claim.label) },
                            supportingContent = { Text("${claim.source} · ${claim.evidenceGrade}") },
                            trailingContent = {
                                TextButton(onClick = { onOpenClaim(claim) }) { Text(stringResource(R.string.auto_cf9b77061f7b)) }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
