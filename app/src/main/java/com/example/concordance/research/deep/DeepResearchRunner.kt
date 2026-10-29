package com.example.concordance.research.deep

import com.example.concordance.research.DeepResearchReport
import com.example.concordance.research.DeepResearchSystem
import com.example.concordance.research.InsightsAnalyzer
import com.example.concordance.research.ResearchGrader
import com.example.concordance.research.ResearchInsight
import com.example.concordance.research.ExperimentPlanner
import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule
import java.io.File

/**
 * Runs a deep research round and, optionally, writes it to a directory of md files.
 *
 * The LLM is not involved here: grading, insight extraction and experiment proposal are
 * deterministic, so the same inputs produce the same manifest hash.
 */
object DeepResearchRunner {

    data class Result(
        val report: DeepResearchReport,
        val manifest: ResearchFileWriter.Manifest?,
        val files: Map<String, String>,
    )

    fun run(
        question: String,
        papers: List<com.example.concordance.research.ResearchPaper>,
        anchors: List<Anchor>,
        capsules: List<Capsule>,
        challengeResolutionRate: Double,
        writeFiles: Boolean,
        runDir: File? = null,
        runId: String = "round-${papers.size}",
        createdAtUtc: String,
        hmacKey: String? = null,
    ): Result {
        val report = DeepResearchSystem.run(papers, anchors, capsules, challengeResolutionRate)

        val files = buildFiles(question, report)

        val manifest = if (writeFiles && runDir != null) {
            ResearchFileWriter.write(
                runDir = runDir,
                runId = runId,
                createdAtUtc = createdAtUtc,
                files = files,
                hmacKey = hmacKey,
            )
        } else {
            null
        }

        return Result(report = report, manifest = manifest, files = files)
    }

    private fun buildFiles(question: String, report: DeepResearchReport): Map<String, String> {
        val files = linkedMapOf<String, String>()

        files["idea.md"] = buildString {
            append("# Research question\n\n")
            append(question.trim().ifBlank { "(no question recorded)" })
            append("\n")
        }

        files["hypothesis.md"] = buildString {
            append("# Hypotheses\n\n")
            if (report.experimentProposals.isEmpty()) {
                append("_No proposals generated._\n")
            } else {
                report.experimentProposals.forEach { p ->
                    append("## ${p.title}\n\n")
                    append("**Priority:** ${p.priority.label}  \n")
                    append("**Estimated impact:** ${"%.2f".format(p.estimatedImpact)}  \n")
                    append("**Hypothesis:** ${p.hypothesis}\n\n")
                    append("Rationale: ${p.rationale}\n\n")
                }
            }
        }

        files["literature.md"] = buildString {
            append("# Literature\n\n")
            append("Papers graded: ${report.papersGraded}\n\n")
            report.grades.forEach { g ->
                append("## ${g.title}\n\n")
                append("Composite: ${g.composite}/100 (${g.grade})  \n")
                append("Weakest dimension: ${g.weakestDimension}  \n\n")
            }
        }

        files["analysis.md"] = buildString {
            append("# Analysis\n\n")
            append("Breakthrough score: ${"%.4f".format(report.overallBreakthroughScore)}\n\n")
            append("Top weakness: ${report.topWeakness}\n\n")
            append("## Insights\n\n")
            if (report.insights.isEmpty()) {
                append("_No insights._\n")
            } else {
                report.insights.forEach { i ->
                    append("- **${i.title}** (${i.type.label}, confidence ${"%.2f".format(i.confidence)}): ${i.description}\n")
                }
            }
        }

        files["experiment_plan.md"] = buildString {
            append("# Experiment plan\n\n")
            report.experimentProposals.forEach { p ->
                append("## ${p.title}\n\n")
                append("- Priority: ${p.priority.label}\n")
                append("- Estimated impact: ${"%.2f".format(p.estimatedImpact)}\n")
                append("- Based on papers: ${p.basedOnPaperIds.joinToString(", ").ifBlank { "none" }}\n")
                append("- Based on insights: ${p.basedOnInsightIds.joinToString(", ").ifBlank { "none" }}\n\n")
            }
        }

        files["paper_outline.md"] = buildString {
            append("# Paper outline\n\n")
            append("1. Introduction\n2. Methods\n3. Results\n4. Discussion\n5. Limitations\n")
        }

        files["paper.md"] = buildString {
            append("# Draft\n\n")
            append(report.summary)
            append("\n")
        }

        files["results.json"] = buildString {
            append("{\n")
            append("  \"papersGraded\": ${report.papersGraded},\n")
            append("  \"insights\": ${report.insights.size},\n")
            append("  \"proposals\": ${report.experimentProposals.size},\n")
            append("  \"breakthroughScore\": ${"%.4f".format(report.overallBreakthroughScore)},\n")
            append("  \"topWeakness\": \"${report.topWeakness}\"\n")
            append("}\n")
        }

        return files
    }
}
