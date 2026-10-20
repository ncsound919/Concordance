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

@Composable
fun AuthScreen(
    onSignedIn: (SupabaseSession) -> Unit,
    onGuest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { createSupabaseSessionStore(context) }
    val auth = remember { SupabaseAuth(store) }
    var email by remember { mutableStateOf("citizen@concordance.dev") }
    var password by remember { mutableStateOf("demo1234") }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Concordance Account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Same account for the phone app and the website.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
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
        TextButton(onClick = onGuest) { Text("Continue as guest (offline only)") }
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