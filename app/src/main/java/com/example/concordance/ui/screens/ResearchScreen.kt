package com.example.concordance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.concordance.research.experiment.CatalogEntry
import com.example.concordance.research.experiment.SpecStatus
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.AppSectionCard
import com.example.ui.theme.*

@Composable
fun ResearchScreen(
    viewModel: ConcordanceViewModel,
    modifier: Modifier = Modifier
) {
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle(initialValue = "")
    val isRunning by viewModel.isRunningLearningRound.collectAsStateWithLifecycle()
    val deepReport by viewModel.deepResearchReport.collectAsStateWithLifecycle()
    val catalog by viewModel.experimentCatalog.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightDeep)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "RESEARCH EXPERIMENT RUNNER",
            style = MaterialTheme.concordance.overline,
            color = ElectricCyan,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Each experiment is a typed protocol with a pinned dataset, a pinned method and a " +
                "decision rule written before the result. Nothing here claims to be launchable unless it is.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary
        )

        AppSectionCard(
            title = "Step 1 - Load Research Data",
            subtitle = "Seeds 15 questions + 10 cross-domain claims, then the typed experiment catalog"
        ) {
            // The button stays available after a successful load: if the catalog read fails, the
            // user must be able to retry instead of being locked behind a one-shot control.
            Button(
                onClick = {
                    viewModel.seedResearchQuestions()
                    viewModel.seedCrossDomainExperiments()
                    viewModel.loadExperimentCatalog()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
            ) {
                Text(
                    if (catalog.isEmpty()) "Load All Research Data" else "Reload Research Data",
                    style = MaterialTheme.concordance.buttonSmall
                )
            }
            if (catalog.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Claims seeded. Experiment catalog: ${catalog.size} specs " +
                        "(${catalog.count { it.status == SpecStatus.READY }} ready, " +
                        "${catalog.count { it.status == SpecStatus.DESIGN_ONLY }} design-only, " +
                        "${catalog.count { it.status == SpecStatus.UNSUPPORTED }} blocked)",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextPrimary
                )
            }
        }

        AppSectionCard(title = "Step 2 - Run Deep Research Loop", subtitle = "Grades papers, finds insights, proposes experiments") {
            Button(
                onClick = { viewModel.runDeepResearch() },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MidnightDeep)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (isRunning) "Running..." else "Run Deep Research Loop", style = MaterialTheme.concordance.buttonSmall)
            }
            if (deepReport != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Breakthrough score: " + "%.2f".format(deepReport!!.overallBreakthroughScore),
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text("Papers graded: " + deepReport!!.papersGraded, style = MaterialTheme.concordance.caption, color = TextSecondary)
                Text("Insights: " + deepReport!!.insights.size, style = MaterialTheme.concordance.caption, color = TextSecondary)
                Text("Proposals: " + deepReport!!.experimentProposals.size, style = MaterialTheme.concordance.caption, color = TextSecondary)
                Text("Top weakness: " + deepReport!!.topWeakness, style = MaterialTheme.concordance.caption, color = TextSecondary)
            }
        }

        if (uiNotice.isNotEmpty()) {
            Text(uiNotice, style = MaterialTheme.concordance.bodyDense, color = ElectricCyan)
        }

        if (catalog.isNotEmpty()) {
            AppSectionCard(
                title = "Step 3 - Experiment Catalog",
                subtitle = "What can actually run, and what is still missing"
            ) {
                catalog.forEach { entry ->
                    ExperimentCatalogRow(entry)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ExperimentCatalogRow(entry: CatalogEntry) {
    val statusColor = when (entry.status) {
        SpecStatus.READY -> Color(0xFF00E676)
        SpecStatus.DESIGN_ONLY -> Color(0xFF9E9E9E)
        SpecStatus.UNSUPPORTED -> Color(0xFFFFD740)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.concordance.bodyDense,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = entry.status.name,
                style = MaterialTheme.concordance.caption,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${entry.spec.experimentClass.label} · ${entry.id} · v${entry.spec.version}",
            style = MaterialTheme.concordance.caption,
            color = TextSecondary
        )
        Text(
            text = entry.reason,
            style = MaterialTheme.concordance.caption,
            color = TextSecondary
        )
    }
}
