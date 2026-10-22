package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.screens.AdjudicationScreen
import com.example.concordance.ui.screens.AgentScreen
import com.example.concordance.ui.screens.CapsulesScreen
import com.example.concordance.ui.screens.IntegrationsScreen
import com.example.concordance.ui.screens.PairedDataScreen
import com.example.concordance.integration.supabase.SupabaseSession
import com.example.concordance.integration.supabase.createSupabaseSessionStore
import com.example.concordance.ui.screens.AuthScreen
import com.example.concordance.ui.navigation.ConcordanceScaffold
import com.example.concordance.ui.navigation.NavigationState
import com.example.concordance.ui.screens.RegistryScreen
import com.example.ui.theme.Skin
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.createSkinPreferenceStore
import com.example.ui.theme.createThemePreferenceStore
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Primary destinations shown in the navigation bar/rail.
 *
 * Material guidance caps a bottom bar at 5 items; the previous design had 7,
 * which made labels illegible and split attention. Secondary destinations
 * (Integrations, Account) live behind "More".
 */
enum class NavigationDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    ADJUDICATE("adjudicate", "Adjudicate", Icons.Default.Science),
    AGENT("agent", "Agent", Icons.Default.CloudSync),
    REGISTRY("registry", "Registry", Icons.Default.Dns),
    CAPSULES("capsules", "Capsules", Icons.Default.Verified),
    PAIRED_POWER("paired_power", "Paired", Icons.Default.Analytics),
    INTEGRATIONS("integrations", "Integrations", Icons.Default.Hub),
    ACCOUNT("account", "Account", Icons.Default.AccountCircle);

    companion object {
        val primary: List<NavigationDestination> = listOf(
            ADJUDICATE, AGENT, REGISTRY, CAPSULES, PAIRED_POWER
        )
        val secondary: List<NavigationDestination> = listOf(INTEGRATIONS, ACCOUNT)
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: ConcordanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.concordance.integration.supabase.SupabaseSyncWorker.schedule(applicationContext)
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val themeStore = remember { createThemePreferenceStore(context) }
            val skinStore = remember { createSkinPreferenceStore(context) }
            var themeMode by rememberSaveable { mutableStateOf(themeStore.load()) }
            var skin by rememberSaveable { mutableStateOf(skinStore.load()) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            MyApplicationTheme(darkTheme = darkTheme, skin = skin) {
                ConcordanceApp(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    skin = skin,
                    onCycleTheme = {
                        themeMode = when (themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.SYSTEM
                        }
                        themeStore.save(themeMode)
                    },
                    onSelectSkin = {
                        skin = it
                        skinStore.save(it)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordanceApp(
    viewModel: ConcordanceViewModel,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    skin: Skin = Skin.CLINICAL,
    onCycleTheme: () -> Unit = {},
    onSelectSkin: (Skin) -> Unit = {},
) {
    val navState = rememberSaveable(saver = NavigationState.Saver) { NavigationState() }
    var showRegulatoryDialog by rememberSaveable { mutableStateOf(false) }
    var showMigrationDialog by rememberSaveable { mutableStateOf(false) }
    var showSkinPicker by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionStore = remember { createSupabaseSessionStore(context) }
    var supabaseSession by remember { mutableStateOf(sessionStore.load()) }

    LaunchedEffect(viewModel.uiNotice) {
        viewModel.uiNotice.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    ConcordanceScaffold(
        navState = navState,
        snackbarHostState = snackbarHostState,
        themeMode = themeMode,
        onCycleTheme = onCycleTheme,
        onOpenSkinPicker = { showSkinPicker = true },
        onInfo = { showRegulatoryDialog = true },
    ) {
        when (navState.current) {
            NavigationDestination.ADJUDICATE -> {
                AdjudicationScreen(
                    viewModel = viewModel,
                    onNavigateToRegistry = { navState.select(NavigationDestination.REGISTRY) }
                )
            }
            NavigationDestination.AGENT -> {
                AgentScreen(viewModel = viewModel)
            }
            NavigationDestination.REGISTRY -> {
                RegistryScreen(
                    viewModel = viewModel,
                    onSelectAndAdjudicate = { _ ->
                        navState.select(NavigationDestination.ADJUDICATE)
                    }
                )
            }
            NavigationDestination.CAPSULES -> {
                CapsulesScreen(viewModel = viewModel)
            }
            NavigationDestination.PAIRED_POWER -> {
                PairedDataScreen(
                    viewModel = viewModel,
                    onNavigateToAdjudication = { navState.select(NavigationDestination.ADJUDICATE) }
                )
            }
            NavigationDestination.INTEGRATIONS -> {
                IntegrationsScreen(
                    viewModel = viewModel,
                    onNavigateToAdjudication = { navState.select(NavigationDestination.ADJUDICATE) }
                )
            }
            NavigationDestination.ACCOUNT -> {
                if (supabaseSession == null) {
                    AuthScreen(
                        onSignedIn = { s ->
                            sessionStore.save(s)
                            supabaseSession = s
                            showMigrationDialog = true
                        },
                        onGuest = { navState.select(NavigationDestination.ADJUDICATE) }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(com.example.ui.theme.Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Signed in as ${supabaseSession?.email ?: "user"}",
                            style = MaterialTheme.concordance.cardTitle,
                            color = TextPrimary
                        )
                        com.example.concordance.ui.components.SupabaseStatusCard(
                            onSyncNow = {
                                com.example.concordance.integration.supabase.SupabaseSyncWorker.runNow(context)
                            }
                        )
                        Button(onClick = {
                            sessionStore.clear()
                            supabaseSession = null
                        }) { Text("Sign out") }
                    }
                }
            }
        }
    }

    if (showRegulatoryDialog) {
        RegulatoryDisclaimerDialog(onDismiss = { showRegulatoryDialog = false })
    }

    if (showSkinPicker) {
        com.example.concordance.ui.components.SkinPickerDialog(
            current = skin,
            onSelect = { onSelectSkin(it) },
            onDismiss = { showSkinPicker = false },
        )
    }

    if (showMigrationDialog) {
        AlertDialog(
            onDismissRequest = { showMigrationDialog = false },
            title = { Text("Upload local data?") },
            text = { Text("You have local claims/capsules on this device. Upload them to your account so they sync across devices?") },
            confirmButton = {
                Button(onClick = {
                    showMigrationDialog = false
                    com.example.concordance.integration.supabase.SupabaseSyncWorker.runNow(context)
                }) { Text("Upload") }
            },
            dismissButton = {
                TextButton(onClick = { showMigrationDialog = false }) { Text("Not now") }
            },
            containerColor = SlateCard
        )
    }
}

@Composable
fun RegulatoryDisclaimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ClinicalTeal)
                Text(
                    text = "Regulatory and Scope Declaration",
                    style = MaterialTheme.concordance.cardTitle,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Research Workflow Tool",
                    style = MaterialTheme.concordance.cardTitle,
                    color = ElectricCyan
                )
                Text(
                    text = "Concordance is a scientific hypothesis adjudication tool for researchers and oncologists evaluating mutation organotropism claims against assay detection artifact boundaries. It is not a clinical decision support device or diagnostic instrument.",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary
                )
                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Transparency and Auditability",
                    style = MaterialTheme.concordance.cardTitle,
                    color = ElectricCyan
                )
                Text(
                    text = "All adjudications produce cryptographically hashed capsules (SHA-256) disclosing full assumptions, mathematical formulas, and limitation conditions to support independent scientific review.",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Text("Understood")
            }
        },
        containerColor = SlateCard
    )
}
