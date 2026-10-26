package com.example.concordance.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.NavigationDestination
import com.example.ui.theme.ClinicalTeal
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LocalSkin
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.blueprintGrid
import com.example.ui.theme.concordance
import com.example.ui.theme.rememberBrandBrush

/**
 * The app shell: top bar, bottom navigation (phones) or navigation rail
 * (>= 600dp), the "More" sheet, and predictable back handling.
 *
 * Extracted from `MainActivity` so the whole shell can be rendered in a JVM
 * test with a fake content slot, without constructing a ViewModel or a Room
 * database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordanceScaffold(
    navState: NavigationState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onCycleTheme: () -> Unit = {},
    onOpenSkinPicker: () -> Unit = {},
    onInfo: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    // A rail needs vertical room for seven destinations. At 600dp wide but only 384dp
    // tall (a phone turned sideways) its lower items were clipped behind the system
    // bar, so the height matters as much as the width.
    val configuration = LocalConfiguration.current
    val useRail = configuration.screenWidthDp >= 600 && configuration.screenHeightDp >= 480
    val skin = LocalSkin
    // Layered skins tint the selected destination; the website's flat
    // treatment uses a plain raised surface instead.
    val indicatorColor = if (skin.layered) ElectricCyan.copy(alpha = 0.14f) else SlateCardElevated

    val themeIcon: ImageVector = when (themeMode) {
        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
        ThemeMode.DARK -> Icons.Default.DarkMode
        ThemeMode.LIGHT -> Icons.Default.LightMode
    }
    val themeLabel = when (themeMode) {
        ThemeMode.SYSTEM -> "Theme: follow system"
        ThemeMode.DARK -> "Theme: dark"
        ThemeMode.LIGHT -> "Theme: light"
    }

    // Predictable back behaviour: close transient UI, then return to the home tab.
    BackHandler(enabled = navState.showMore || navState.current != NavigationDestination.ADJUDICATE) {
        navState.back()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MidnightDeep,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SlateCardElevated,
                    contentColor = TextPrimary,
                    actionColor = ElectricCyan,
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        topBar = {
            // The wordmark sits in a fixed-height bar, and at 1.3x font scale it wrapped
            // "CONCORDANC / E" off the edge. A brand name is exempt from text resizing
            // (WCAG 1.4.4), so cap its scale; dividing by the live scale makes the cap
            // the size that actually reaches the screen.
            val fontScale = LocalDensity.current.fontScale
            val wordmark = MaterialTheme.concordance.sectionTitle
            val wordmarkScale = fontScale.coerceAtMost(1.15f) / fontScale
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    if (skin.layered) {
                                        Modifier.background(rememberBrandBrush())
                                    } else {
                                        Modifier
                                            .background(ElectricCyan.copy(alpha = 0.12f))
                                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "v0.1",
                                style = MaterialTheme.concordance.chip,
                                color = if (skin.layered) MidnightDeep else ElectricCyan,
                            )
                        }
                        Column {
                            Text(
                                text = "CONCORDANCE",
                                style = wordmark,
                                fontSize = (wordmark.fontSize.value * wordmarkScale).sp,
                                lineHeight = (wordmark.fontSize.value * 1.25f * wordmarkScale).sp,
                                color = TextPrimary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Detection-Corrected",
                                style = MaterialTheme.concordance.caption,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSkinPicker) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Change the app's appearance",
                            tint = ClinicalTeal,
                        )
                    }
                    IconButton(onClick = onCycleTheme) {
                        Icon(themeIcon, contentDescription = themeLabel, tint = ClinicalTeal)
                    }
                    if (!useRail) {
                        IconButton(onClick = navState::openMore) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More sections",
                                tint = ClinicalTeal,
                            )
                        }
                    }
                    actions()
                    IconButton(onClick = onInfo) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Regulatory classification and scope",
                            tint = ClinicalTeal,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateCard,
                    titleContentColor = TextPrimary,
                ),
            )
        },
        bottomBar = {
            if (!useRail) {
                NavigationBar(
                    containerColor = SlateCard,
                    contentColor = ElectricCyan,
                    windowInsets = WindowInsets.navigationBars,
                ) {
                    NavigationDestination.primary.forEach { dest ->
                        val selected = navState.current == dest
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navState.select(dest) },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = dest.title,
                                    tint = if (selected) ElectricCyan else TextSecondary,
                                )
                            },
                            label = {
                                Text(
                                    text = dest.title,
                                    style = MaterialTheme.concordance.caption,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) ElectricCyan else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = indicatorColor,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (useRail) {
                // Fixed box: on this Material3 version the rail measured itself at the
                // row's full width and squeezed the screen content to zero, so landscape
                // showed a centred column of nav items over an empty page.
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight(),
                ) {
                    NavigationRail(
                        containerColor = SlateCard,
                        header = {
                            IconButton(onClick = onCycleTheme) {
                                Icon(themeIcon, contentDescription = themeLabel, tint = ClinicalTeal)
                            }
                        },
                    ) {
                        Spacer(Modifier.weight(1f))
                        NavigationDestination.primary.forEach { dest ->
                            val selected = navState.current == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navState.select(dest) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.title,
                                        tint = if (selected) ElectricCyan else TextSecondary,
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.title,
                                        style = MaterialTheme.concordance.caption,
                                        color = if (selected) ElectricCyan else TextSecondary,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = indicatorColor,
                                ),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = SlateCardBorder)
                        NavigationDestination.secondary.forEach { dest ->
                            val selected = navState.current == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navState.select(dest) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.title,
                                        tint = if (selected) ElectricCyan else TextSecondary,
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.title,
                                        style = MaterialTheme.concordance.caption,
                                        color = if (selected) ElectricCyan else TextSecondary,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = indicatorColor,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // fillMaxHeight, not fillMaxSize: a weighted child that also fills the
            // parent's width steals the whole row from the rail.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .then(
                        if (skin.blueprintBackdrop) {
                            Modifier.blueprintGrid(ElectricCyan.copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    ),
            ) {
                content()
            }
        }
    }

    if (navState.showMore) {
        ModalBottomSheet(
            onDismissRequest = { navState.closeMore() },
            containerColor = SlateCard,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "More",
                    style = MaterialTheme.concordance.cardTitle,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                NavigationDestination.secondary.forEach { dest ->
                    NavigationDrawerItem(
                        label = {
                            Text(dest.title, style = MaterialTheme.concordance.bodyDense)
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        selected = navState.current == dest,
                        onClick = { navState.select(dest) },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = SlateCard,
                            selectedContainerColor = SlateCardElevated,
                            unselectedTextColor = TextSecondary,
                            selectedTextColor = ElectricCyan,
                            unselectedIconColor = TextSecondary,
                            selectedIconColor = ElectricCyan,
                        ),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
