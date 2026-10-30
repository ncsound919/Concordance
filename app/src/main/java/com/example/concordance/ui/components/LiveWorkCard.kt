package com.example.concordance.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.concordance.autonomy.ActivityEntry
import com.example.concordance.autonomy.ActivityLevel
import com.example.concordance.autonomy.AutonomyActivity
import com.example.concordance.autonomy.AutonomyState
import com.example.ui.theme.ClinicalTeal
import com.example.ui.theme.Dimens
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividCoral
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.concordance
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.example.R

/**
 * The "what is running right now" panel for the background automation.
 *
 * The autonomy state on its own is a set of counters. This card pairs those counters
 * with the activity log so a step that is in flight shows as WORKING with a live timer,
 * and a step that failed says so instead of leaving the screen looking idle.
 */
@Composable
fun LiveWorkCard(
    state: AutonomyState,
    entries: List<ActivityEntry>,
    onGo: (String) -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRunStep: () -> Unit,
    onCancelSlow: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One tick a second keeps the "running for" timer honest without recomposing the
    // rest of the screen (only this card reads `now`).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val active = entries.firstOrNull { it.level == ActivityLevel.RUNNING }
    val lastFinished = entries.firstOrNull { it.level != ActivityLevel.RUNNING }

    // The question the run is for. Without it Go used to research the literal word
    // "research", which produced a report about nothing.
    var goal by remember { mutableStateOf(state.currentGoal.orEmpty()) }
    LaunchedEffect(state.currentGoal) {
        if (goal.isBlank() && state.currentGoal != null) goal = state.currentGoal.orEmpty()
    }

    AppSectionCard(
        title = "Live work",
        subtitle = "Background automation and what it is doing",
        modifier = modifier,
    ) {
        // Headline: working now, or when the last piece of work finished.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            StatusDot(
                color = if (active != null) ElectricCyan else TextMuted,
                pulsing = active != null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (active != null) "WORKING" else "IDLE",
                    style = MaterialTheme.concordance.overline,
                    color = if (active != null) ElectricCyan else TextSecondary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        active != null -> active.message
                        lastFinished != null ->
                            "Last: ${lastFinished.message} (${AutonomyActivity.formatAge(now, lastFinished.at)})"
                        else -> "Nothing has run yet. Press Go, or run one step."
                    },
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active != null) {
                    Text(
                        text = "running ${AutonomyActivity.formatDuration(now - active.at)}",
                        style = MaterialTheme.concordance.monoId,
                        color = ElectricCyan,
                    )
                }
            }
        }

        // Counters the workers keep: a glance answers "what state is the machine in".
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                StatCell("Mode", state.mode.label, Modifier.weight(1f))
                StatCell("Step", state.currentStep.toString(), Modifier.weight(1f))
                StatCell("Queue", state.queueSize.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                StatCell("Outbox", state.outboxCount.toString(), Modifier.weight(1f))
                StatCell(
                    "Battery",
                    if (state.batteryOk) "OK" else "Low",
                    Modifier.weight(1f),
                    valueColor = if (state.batteryOk) NeonEmerald else VividCoral
                )
                StatCell(
                    "Network",
                    if (state.online) "Online" else "Offline",
                    Modifier.weight(1f),
                    valueColor = if (state.online) NeonEmerald else WarningAmber
                )
            }
            if (state.currentGoal != null) {
                Text(
                    text = "Goal: ${state.currentGoal}",
                    style = MaterialTheme.concordance.caption,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            label = { Text(stringResource(R.string.auto_8b862c6ab8e1), style = MaterialTheme.concordance.bodyDense) },
            placeholder = {
                Text(stringResource(R.string.auto_2a1151e4c59b),
                    style = MaterialTheme.concordance.caption
                )
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.concordance.bodyDense,
            maxLines = 3
        )
        Text(
            text = if (goal.isBlank()) {
                "Go is disabled until there is a goal: the agent designs experiments for this question."
            } else {
                "Go asks the agent for a plan and experiments on this question."
            },
            style = MaterialTheme.concordance.caption,
            color = if (goal.isBlank()) WarningAmber else TextSecondary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
            Button(
                onClick = { onGo(goal.trim()) },
                enabled = goal.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClinicalTeal,
                    contentColor = MidnightDeep
                )
            ) {
                Text("Go", style = MaterialTheme.concordance.buttonSmall)
            }
            Button(
                onClick = onPause,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningAmber,
                    contentColor = MidnightDeep
                )
            ) {
                Text(stringResource(R.string.auto_781961bc81c2), style = MaterialTheme.concordance.buttonSmall)
            }
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SlateCardElevated,
                    contentColor = TextPrimary
                )
            ) {
                Text(stringResource(R.string.auto_9e253470c876), style = MaterialTheme.concordance.buttonSmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)) {
            TextButton(onClick = onRunStep) {
                Text(stringResource(R.string.auto_be438b4e40fb),
                    style = MaterialTheme.concordance.bodyDense,
                    color = ElectricCyan
                )
            }
            TextButton(onClick = onCancelSlow) {
                Text(stringResource(R.string.auto_6ef2db1dec6f),
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary
                )
            }
        }

        // The log itself: newest first, capped so the card stays scannable.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.auto_72d5617f3f47),
                style = MaterialTheme.concordance.overline,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClearLog) {
                    Text(stringResource(R.string.auto_719ea396ad92),
                        style = MaterialTheme.concordance.caption,
                        color = TextSecondary
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Text(text = stringResource(R.string.auto_94cbec1d8340),
                style = MaterialTheme.concordance.caption,
                color = TextMuted
            )
        } else {
            entries.take(6).forEach { entry ->
                ActivityRow(entry = entry, now = now, isActive = entry.level == ActivityLevel.RUNNING)
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry, now: Long, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        StatusDot(color = levelColor(entry.level), pulsing = isActive, modifier = Modifier.padding(top = 5.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${entry.source} · ${entry.level.label}",
                    style = MaterialTheme.concordance.monoId,
                    color = levelColor(entry.level),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = entry.durationMs?.let { AutonomyActivity.formatDuration(it) }
                        ?: AutonomyActivity.formatAge(now, entry.at),
                    style = MaterialTheme.concordance.caption,
                    color = TextMuted
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.concordance.caption,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    Column(
        modifier = modifier
            .background(SlateCardElevated, Dimens.ShapeS)
            .padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXS),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.concordance.caption,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.concordance.bodyDense,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "work-pulse")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "work-pulse-alpha"
        )
        value
    } else {
        1f
    }
    Box(
        modifier = modifier
            .size(9.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

@Composable
@ReadOnlyComposable
private fun levelColor(level: ActivityLevel): Color = when (level) {
    ActivityLevel.RUNNING -> ElectricCyan
    ActivityLevel.OK -> NeonEmerald
    ActivityLevel.SKIPPED -> WarningAmber
    ActivityLevel.FAILED -> VividCoral
}
