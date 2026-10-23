package com.example.concordance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.concordance.integration.api.CohortStudy
import com.example.concordance.integration.api.CuratedVariantEvidence
import com.example.concordance.integration.api.SomaticMutation
import com.example.concordance.ui.ConcordanceViewModel
import com.example.ui.theme.*

/**
 * Curated variant evidence (CIViC) and public cohorts (cBioPortal). Both
 * providers are keyless and open; results carry provenance from the shared
 * HttpJsonClient.
 */
@Composable
fun CuratedEvidenceTab(
    viewModel: ConcordanceViewModel,
    onCopyText: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val evidence by viewModel.curatedEvidence.collectAsState()
    val cohorts by viewModel.cohortStudies.collectAsState()
    val mutations by viewModel.somaticMutations.collectAsState()
    val isSearching by viewModel.isSearchingCurated.collectAsState()

    var query by remember { mutableStateOf("ESR1") }
    var mutationStudy by remember { mutableStateOf("brca_tcga") }
    var mutationGene by remember { mutableStateOf("ESR1") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CURATED VARIANT & COHORT EVIDENCE",
                    style = MaterialTheme.concordance.overline,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
                Text(
                    text = "CIViC curates variant-level clinical interpretations (therapy sensitivity/resistance). " +
                            "cBioPortal indexes public cancer-genomics cohorts. Both are keyless and open.",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Gene symbol or cohort keyword (e.g. ESR1, breast)", style = MaterialTheme.concordance.caption) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.concordance.bodyDense
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.searchCuratedEvidence(query) },
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CIViC Evidence", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.searchCohorts(query) },
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("cBioPortal Cohorts", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isSearching) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricCyan,
                    trackColor = SlateCardBorder
                )
            }
        }

        if (evidence.isNotEmpty()) {
            item { SectionLabel("CIViC CURATED EVIDENCE (${evidence.size})") }
            items(evidence) { entry ->
                CuratedEvidenceCard(entry, onCopyText) { viewModel.importCuratedEvidenceAsClaim(entry) }
            }
        }

        if (cohorts.isNotEmpty()) {
            item { SectionLabel("CBIOBORTAL COHORTS (${cohorts.size})") }
            items(cohorts) { study ->
                CohortStudyCard(study, onCopyText)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("SOMATIC MUTATIONS (cBioPortal)", style = MaterialTheme.concordance.overline, fontWeight = FontWeight.Bold, color = ElectricCyan)
                Text(
                    "Pull sample-level mutation calls for a gene within a study's mutation profile.",
                    style = MaterialTheme.concordance.bodyDense,
                    color = TextSecondary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mutationStudy,
                        onValueChange = { mutationStudy = it },
                        placeholder = { Text("study id (e.g. brca_tcga)", style = MaterialTheme.concordance.caption) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                    OutlinedTextField(
                        value = mutationGene,
                        onValueChange = { mutationGene = it },
                        placeholder = { Text("gene (ESR1)", style = MaterialTheme.concordance.caption) },
                        modifier = Modifier.weight(0.7f),
                        textStyle = MaterialTheme.concordance.bodyDense
                    )
                }
                Button(
                    onClick = { viewModel.searchSomaticMutations(mutationStudy, mutationGene) },
                    enabled = !isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = MidnightDeep),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fetch Mutations", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (mutations.isNotEmpty()) {
            item { SectionLabel("SOMATIC MUTATIONS (${mutations.size})") }
            items(mutations) { m -> SomaticMutationCard(m) }
        }
    }
}

@Composable
private fun SomaticMutationCard(m: SomaticMutation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(m.geneSymbol, style = MaterialTheme.concordance.chip, fontWeight = FontWeight.Bold, color = ElectricCyan)
            Text(m.mutationType, style = MaterialTheme.concordance.caption, color = TextMuted)
        }
        Text(
            text = "${m.sampleId} • ${m.proteinChange}",
            style = MaterialTheme.concordance.monoId,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = listOfNotNull(
                m.chr?.let { "chr$it" },
                m.startPosition?.let { "pos $it" },
                m.tumorAltCount?.let { "alt $it" },
                m.tumorRefCount?.let { "ref $it" },
                m.mutationStatus
            ).joinToString(" • "),
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.concordance.caption,
        fontWeight = FontWeight.Bold,
        color = TextMuted
    )
}

@Composable
private fun LevelBadge(level: String, rating: Int?) {
    val label = if (rating != null) "Level $level • ${rating}★" else "Level $level"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ElectricCyan.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.concordance.chip, color = ElectricCyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CuratedEvidenceCard(
    item: CuratedVariantEvidence,
    onCopyText: (String, String) -> Unit,
    onImport: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.gene} ${item.variant}",
                style = MaterialTheme.concordance.cardTitle,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan
            )
            LevelBadge(item.evidenceLevel, item.evidenceRating)
        }

        Text(
            text = "${item.significance} • ${item.evidenceDirection} • ${item.evidenceType}",
            style = MaterialTheme.concordance.caption,
            color = ClinicalTeal,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Disease: ${item.disease.ifBlank { "unspecified" }} | Therapies: " +
                    item.therapies.joinToString(", ").ifBlank { "none" },
            style = MaterialTheme.concordance.caption,
            color = TextMuted
        )

        if (item.variantTypes.isNotEmpty()) {
            Text(
                text = "Variant type: ${item.variantTypes.joinToString(", ")}",
                style = MaterialTheme.concordance.caption,
                color = TextMuted
            )
        }

        Text(
            text = item.description,
            style = MaterialTheme.concordance.bodyDense,
            color = TextSecondary,
            lineHeight = 15.sp,
            maxLines = 4
        )

        OutlinedButton(
            onClick = { onCopyText("CIViC Evidence URL", item.civicUrl) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy CIViC Evidence #${item.civicEvidenceId} Link", style = MaterialTheme.concordance.buttonSmall)
        }

        Button(
            onClick = onImport,
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = MidnightDeep),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import as Draft Claim", style = MaterialTheme.concordance.buttonSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CohortStudyCard(
    study: CohortStudy,
    onCopyText: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCard)
            .border(1.dp, SlateCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = study.studyId,
                style = MaterialTheme.concordance.monoId,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = study.cancerType ?: "cohort",
                style = MaterialTheme.concordance.caption,
                color = TextMuted
            )
        }

        Text(
            text = study.name.ifBlank { study.studyId },
            style = MaterialTheme.concordance.bodyDense,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            lineHeight = 16.sp
        )

        Text(
            text = study.description,
            style = MaterialTheme.concordance.bodyDense,
            color = TextMuted,
            maxLines = 3,
            lineHeight = 15.sp
        )

        Text(
            text = listOfNotNull(
                study.sampleCount?.let { "$it samples" },
                study.referenceGenome?.let { "genome $it" },
                study.pmid?.let { "PMID $it" }
            ).joinToString(" • "),
            style = MaterialTheme.concordance.monoId,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )

        OutlinedButton(
            onClick = { onCopyText("cBioPortal Study URL", study.url) },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy cBioPortal Study Link", style = MaterialTheme.concordance.buttonSmall)
        }
    }
}
