package com.example.concordance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.concordance.ui.ConcordanceViewModel
import com.example.concordance.ui.components.AppSectionCard
import com.example.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ResearchScreen(
    viewModel: ConcordanceViewModel,
    modifier: Modifier = Modifier
) {
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle(initialValue = "")
    val isRunning by viewModel.isRunningLearningRound.collectAsStateWithLifecycle()
    val deepReport by viewModel.deepResearchReport.collectAsStateWithLifecycle()
    var loaded by mutableStateOf(false)

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
            text = "Run the self-learning research loop on 35 oncology experiments.",
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary
        )

        AppSectionCard(title = "Step 1 - Load Research Data", subtitle = "Seeds 15 questions + 10 cross-domain + 25 sub-experiments") {
            if (!loaded) {
                Button(
                    onClick = {
                        viewModel.seedResearchQuestions()
                        viewModel.seedCrossDomainExperiments()
                        viewModel.seedSubExperiments()
                        loaded = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep)
                ) {
                    Text("Load All Research Data", style = MaterialTheme.concordance.buttonSmall)
                }
            } else {
                Text("Research data loaded (35 anchors, 25 sub-experiments)", style = MaterialTheme.concordance.bodyDense, color = TextPrimary)
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
                Text("Breakthrough score: " + "%.2f".format(deepReport!!.overallBreakthroughScore), style = MaterialTheme.concordance.bodyDense, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Papers graded: " + deepReport!!.papersGraded, style = MaterialTheme.concordance.caption, color = TextSecondary)
                Text("Insights: " + deepReport!!.insights.size, style = MaterialTheme.concordance.caption, color = TextSecondary)
                Text("Proposals: " + deepReport!!.experimentProposals.size, style = MaterialTheme.concordance.caption, color = TextSecondary)
                Text("Top weakness: " + deepReport!!.topWeakness, style = MaterialTheme.concordance.caption, color = TextSecondary)
            }
        }

        AppSectionCard(title = "Step 3 - Launch Sub-Experiments on Kaggle", subtitle = "12 ready-to-run sub-experiments") {
            Text(
                text = "Navigate to Integrations then Kaggle Compute to launch individual sub-experiments. Each has a kaggle-pipeline compute target.",
                style = MaterialTheme.concordance.bodyDense,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            val readySubs = listOf(
                "xd-01a1: Exosome protein to embeddings",
                "xd-01a2: Differential abundance by organ",
                "xd-02a1: Base-editor screen (primary)",
                "xd-03a1: Perturb-CITE-seq library design",
                "xd-04a1: MRI radiomic features (BraTS)",
                "xd-05a1: Spatial transcriptomics (HTAN)",
                "xd-06a1: Exosomal miRNA panel",
                "xd-07a1: Federated learning (FedAvg)",
                "xd-08a1: TMB calculation (TCGA)",
                "xd-09a1: CRISPR 2D drug resistance",
                "xd-10a1: Tumor growth model (PhysiCell)",
                "xd-01c1: Multi-modal fusion (early)"
            )
            readySubs.forEach { sub ->
                Text("- " + sub, style = MaterialTheme.concordance.caption, color = TextPrimary)
            }
        }

        if (uiNotice != null) {
            Text(uiNotice!!, style = MaterialTheme.concordance.bodyDense, color = ElectricCyan)
        }
    }
}
