package com.example.concordance.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Ask for POST_NOTIFICATIONS once per install on Android 13+.
 *
 * The app declares the permission and runs a foreground research service, but
 * never requested it, so on Android 13+ the service notification — the only
 * visible sign that a background run is happening — was suppressed. The system
 * remembers a denial, so this asks at most once.
 */
@Composable
fun NotificationPermissionGate() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("concordance_permissions", Context.MODE_PRIVATE)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the system records the answer; nothing else to do */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (prefs.getBoolean("notification_permission_asked", false)) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        prefs.edit().putBoolean("notification_permission_asked", true).apply()
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
