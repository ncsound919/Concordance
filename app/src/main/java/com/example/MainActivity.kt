package com.example

import android.content.Intent
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
import com.example.concordance.ui.screens.ResearchScreen
import com.example.concordance.integration.supabase.SupabaseSession
import com.example.concordance.links.ConcordanceLinks
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R

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
    ACCOUNT("account", "Account", Icons.Default.AccountCircle),
    RESEARCH("research", "Research", Icons.Default.Science);

    companion object {
        val primary: List<NavigationDestination> = listOf(
            ADJUDICATE, AGENT, REGISTRY, CAPSULES, PAIRED_POWER
        )
        val secondary: List<NavigationDestination> = listOf(INTEGRATIONS, ACCOUNT, RESEARCH)
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: ConcordanceViewModel by viewModels()

    /** Anchor carried by an incoming App Link, consumed once the claims load. */
    private var pendingAnchorId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
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
                com.example.concordance.ui.components.NotificationPermissionGate()
                ConcordanceApp(
                    viewModel = viewModel,
                    deepLinkAnchorId = pendingAnchorId,
                    onDeepLinkHandled = { pendingAnchorId = null },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** Only ACTION_VIEW links are read, and only the validated anchor id is kept. */
    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        pendingAnchorId = ConcordanceLinks.anchorId(intent.data)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordanceApp(
    viewModel: ConcordanceViewModel,
    deepLinkAnchorId: String? = null,
    onDeepLinkHandled: () -> Unit = {},
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
    val claimsForDeepLink by viewModel.claims.collectAsStateWithLifecycle()

    // A link from Sieve/Recourse names an anchor. Select it once the claims are
    // available (a cold start may still be loading) and open adjudication.
    LaunchedEffect(deepLinkAnchorId, claimsForDeepLink) {
        val id = deepLinkAnchorId ?: return@LaunchedEffect
        val anchor = claimsForDeepLink.firstOrNull { it.id == id } ?: return@LaunchedEffect
        viewModel.selectAnchor(anchor)
        navState.select(NavigationDestination.ADJUDICATE)
        onDeepLinkHandled()
    }

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
            NavigationDestination.RESEARCH -> {
                ResearchScreen(viewModel = viewModel)
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
                                com.example.concordance.integration.supabase.SupabaseSyncWorker.setEnabled(context, true)
                        com.example.concordance.integration.supabase.SupabaseSyncWorker.runNow(context)
                            }
                        )
                        Button(onClick = {
                            sessionStore.clear()
                            supabaseSession = null
                        }) { Text(stringResource(R.string.auto_dc1649a16c14)) }
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
            title = { Text(stringResource(R.string.auto_e0049f6b7742)) },
            text = { Text(stringResource(R.string.auto_1b778def4753)) },
            confirmButton = {
                Button(onClick = {
                    showMigrationDialog = false
                    com.example.concordance.integration.supabase.SupabaseSyncWorker.setEnabled(context, true)
                        com.example.concordance.integration.supabase.SupabaseSyncWorker.runNow(context)
                }) { Text(stringResource(R.string.auto_8bdf057f91e7)) }
            },
            dismissButton = {
                TextButton(onClick = { showMigrationDialog = false }) { Text(stringResource(R.string.auto_e45714907316)) }
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
                Text(text = stringResource(R.string.auto_2670dfa38254),
                    style = MaterialTheme.concordance.cardTitle,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.auto_aff822cb0971),
                    style = MaterialTheme.concordance.cardTitle,
                    color = ElectricCyan
                )
                Text(text = stringResource(R.string.auto_6aded0e74f5c),
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary
                )
                HorizontalDivider(color = SlateCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                Text(text = stringResource(R.string.auto_eba0bc28e4bd),
                    style = MaterialTheme.concordance.cardTitle,
                    color = ElectricCyan
                )
                Text(text = stringResource(R.string.auto_2328f5b36c8e),
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
                Text(stringResource(R.string.auto_bc79700b155e))
            }
        },
        containerColor = SlateCard
    )
}
