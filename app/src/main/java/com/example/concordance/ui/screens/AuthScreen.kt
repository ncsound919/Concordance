package com.example.concordance.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.concordance.integration.supabase.SupabaseAuth
import com.example.concordance.integration.supabase.SupabaseSession
import com.example.concordance.integration.supabase.createSupabaseSessionStore
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun AuthScreen(
    onSignedIn: (SupabaseSession) -> Unit,
    onGuest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { createSupabaseSessionStore(context) }
    val auth = remember { SupabaseAuth(store) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.auto_32a39b95e1d4), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.auto_8d82131376f0),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.auto_84add5b29527)) }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.auto_8be3c943b160)) },
            singleLine = true, visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                loading = true; error = null
                scope.launch {
                    val res = if (isSignUp) auth.signUp(email, password, email.substringBefore("@"))
                    else auth.signIn(email, password)
                    loading = false
                    res.onSuccess { s -> if (s != null) onSignedIn(s) else error = "Check your email to verify, then sign in." }
                        .onFailure { error = it.message }
                }
            },
            enabled = !loading
        ) {
            Text(if (isSignUp) "Create account" else "Sign in")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { isSignUp = !isSignUp }) {
            Text(if (isSignUp) "Already have an account? Sign in" else "New here? Create account")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGuest) { Text(stringResource(R.string.auto_d83bf2ce50ff)) }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        if (loading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
    }
}
