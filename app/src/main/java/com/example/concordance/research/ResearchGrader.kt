package com.example.concordance.research

import com.example.concordance.model.Anchor
import com.example.concordance.model.Capsule
import com.example.concordance.model.EvidenceGrade
import com.example.concordance.model.Verdict
import java.util.UUID

/**
 * Computes 10-point grades for research papers / experiment results.
 * Pure, side-effect-free. All inputs are derived from stored claims, capsules,
 * and challenges.
 */
object ResearchGrader {

    /** Grade a single paper from its associated data. */
    fun grade(
        paper: ResearchPaper,
        anchors: List<Anchor>,
        capsules: List<Capsule>,
        challengeResolutionRate: Double
    ): PaperGrade {
        val paperAnchors = anchors.filter { it.id in paper.anchorIds }
        val paperCapsules = capsules.filter { it.id in paper.capsuleIds }

        val novelty = computeNovelty(paper, paperAnchors)
        val rigor = computeRigor(paperAnchors, paperCapsules)
        val statSig = computeStatisticalSignificance(paperAnchors, paperCapsules)
        val repro = computeReproducibility(paperCapsules)
        val impact = computeImpact(paper, paperCapsules)
        val gen = computeGeneralizability(paperAnchors)
        val dataQ = computeDataQuality(paperAnchors)
        val innov = computeInnovation(paper, paperCapsules)
        val breakthrough = computeBreakthrough(novelty, impact, innov, statSig)
        val challengeSurv = (challengeResolutionRate * 10).toInt().coerceIn(0, 10)

        return PaperGrade(
            paperId = paper.id,
            title = paper.title,
            timestampUtc = paper.timestampUtc,
            novelty = novelty,
            methodologicalRigor = rigor,
            statisticalSignificance = statSig,
            reproducibility = repro,
            impactPotential = impact,
            generalizability = gen,
            dataQuality = dataQ,
            innovation = innov,
            breakthroughPotential = breakthrough,
            challengeSurvival = challengeSurv
        )
    }

    /** Grade all papers and return sorted by composite (best first). */
    fun gradeAll(
        papers: List<ResearchPaper>,
        anchors: List<Anchor>,
        capsules: List<Capsule>,
        challengeResolutionRate: Double
    ): List<PaperGrade> =
        papers.map { grade(it, anchors, capsules, challengeResolutionRate) }
            .sortedByDescending { it.composite }

    // --- Dimension computers (each returns 0-10) ---

    private fun computeNovelty(paper: ResearchPaper, anchors: List<Anchor>): Int {
        // More anchors with diverse sources = more novel
        val sourceDiversity = anchors.map { it.source }.distinct().size
        val base = (sourceDiversity * 2).coerceAtMost(6)
        // Abstract-only or synthetic reduces novelty
        val penalty = anchors.count { it.evidenceGrade == EvidenceGrade.ABSTRACT_ONLY || it.evidenceGrade == EvidenceGrade.SYNTHETIC }
        return (base - penalty).coerceIn(0, 10)
    }

    private fun computeRigor(anchors: List<Anchor>, capsules: List<Capsule>): Int {
        var score = 0
        if (anchors.any { it.evidenceGrade == EvidenceGrade.COUNTS }) score += 3
        if (capsules.isNotEmpty()) score += 2
        if (capsules.any { it.provenanceDigest != null }) score += 2
        if (capsules.any { it.provenanceRecordIds.isNotEmpty() }) score += 2
        if (anchors.size >= 2) score += 1
        return score.coerceIn(0, 10)
    }

    private fun computeStatisticalSignificance(anchors: List<Anchor>, capsules: List<Capsule>): Int {
        var score = 0
        val capsule = capsules.firstOrNull()
        if (capsule != null) {
            if (capsule.rSignificance != null && capsule.rSignificance < 0.5) score += 3
            if (capsule.rNull < 0.5) score += 2
            // Narrow CI = more significant
            if (capsule.observedOr < 1.0) score += 2
        }
        if (anchors.any { it.evidenceGrade == EvidenceGrade.COUNTS }) score += 2
        if (anchors.size >= 3) score += 1
        return score.coerceIn(0, 10)
    }

    private fun computeReproducibility(capsules: List<Capsule>): Int {
        var score = 0
        if (capsules.isNotEmpty()) score += 3
        if (capsules.any { it.sha256.isNotBlank() }) score += 3
        if (capsules.any { it.provenanceDigest != null }) score += 2
        if (capsules.any { it.modelRevisionId != null }) score += 2
        return score.coerceIn(0, 10)
    }

    private fun computeImpact(paper: ResearchPaper, capsules: List<Capsule>): Int {
        var score = 0
        if (paper.verdict == Verdict.SUPPORTED) score += 4
        if (paper.verdict == Verdict.NOT_SUPPORTED) score += 2
        if (capsules.isNotEmpty()) score += 2
        if (paper.challengeCount > 0) score += 2
        if (paper.keywords.size >= 3) score += 2
        return score.coerceIn(0, 10)
    }

    private fun computeGeneralizability(anchors: List<Anchor>): Int {
        var score = 0
        if (anchors.size >= 2) score += 3
        if (anchors.size >= 3) score += 2
        val grades = anchors.map { it.evidenceGrade }.distinct().size
        if (grades >= 2) score += 2
        if (anchors.any { it.brainTotal != null && it.brainTotal!! > 100 }) score += 2
        if (anchors.any { it.otherTotal != null && it.otherTotal!! > 1000 }) score += 1
        return score.coerceIn(0, 10)
    }

    private fun computeDataQuality(anchors: List<Anchor>): Int {
        var score = 0
        if (anchors.any { it.hasCounts }) score += 4
        if (anchors.any { it.oddsRatio != null }) score += 2
        if (anchors.any { it.orCiLow != null && it.orCiHigh != null }) score += 2
        if (anchors.all { it.source.isNotBlank() }) score += 2
        return score.coerceIn(0, 10)
    }

    private fun computeInnovation(paper: ResearchPaper, capsules: List<Capsule>): Int {
        var score = 0
        // Capsules with provenance = methodological innovation
        if (capsules.any { it.provenanceDigest != null }) score += 3
        // Re-adjudicated capsules = revision-aware
        if (capsules.any { it.supersedesCapsuleId != null }) score += 3
        // Model revision tracking = versioned science
        if (paper.modelRevisionId != null) score += 2
        // Challenge-driven = iterative science
        if (paper.challengeCount > 0) score += 2
        return score.coerceIn(0, 10)
    }

    private fun computeBreakthrough(novelty: Int, impact: Int, innovation: Int, statSig: Int): Int {
        // Breakthrough = weighted combination of top dimensions
        val raw = (novelty * 0.3 + impact * 0.3 + innovation * 0.2 + statSig * 0.2).toInt()
        return raw.coerceIn(0, 10)
    }
}
