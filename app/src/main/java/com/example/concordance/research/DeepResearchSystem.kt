package com.example.concordance.research

import com.example.concordance.integration.api.ProvenanceRecorder
import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule

/**
 * Orchestrates a deep research round: grades papers, analyzes insights,
 * proposes experiments, and produces a [DeepResearchReport].
 *
 * The report feeds back into the recursive learning system (via telemetry and
 * the learning loop) to inform the next set of experiments.
 */
object DeepResearchSystem {

    /** Run a full deep research round. */
    fun run(
        papers: List<ResearchPaper>,
        anchors: List<Anchor>,
        capsules: List<Capsule>,
        challengeResolutionRate: Double
    ): DeepResearchReport {
        val now = ProvenanceRecorder.nowUtc()

        // 1. Grade all papers
        val grades = ResearchGrader.gradeAll(papers, anchors, capsules, challengeResolutionRate)

        // 2. Analyze insights
        val insights = InsightsAnalyzer.analyzeTrends(grades, papers)

        // 3. Propose next experiments
        val proposals = ExperimentPlanner.propose(grades, insights, papers)

        // 4. Compute overall breakthrough score
        val breakthroughScore = InsightsAnalyzer.breakthroughScore(grades)

        // 5. Top weakness
        val topWeakness = InsightsAnalyzer.topWeakness(grades)

        // 6. Summary
        val summary = buildString {
            append("Deep research round: ${papers.size} papers graded, ${insights.size} insights, ${proposals.size} experiment proposals. ")
            append("Breakthrough score: ${"%.2f".format(breakthroughScore)}. ")
            append("Top weakness: $topWeakness. ")
            if (grades.isNotEmpty()) {
                append("Best paper: ${grades.first().title} (${grades.first().composite}/100, ${grades.first().grade}).")
            }
        }

        return DeepResearchReport(
            timestampUtc = now,
            papersGraded = papers.size,
            grades = grades,
            insights = insights,
            experimentProposals = proposals,
            overallBreakthroughScore = breakthroughScore,
            topWeakness = topWeakness,
            summary = summary
        )
    }
}
