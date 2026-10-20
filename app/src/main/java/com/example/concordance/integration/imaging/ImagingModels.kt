package com.example.concordance.integration.imaging

import com.example.concordance.integration.queue.JobDataClass
import com.example.concordance.integration.queue.WorkerRoutingPolicy

// Represents the extracted table data (ported from concordance.imaging.extract_table.py)
data class TableRow(
    val text: String,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val confidence: Float
)

data class TableReading(
    val engine: String,
    val engineVersion: String,
    val brain: Triple<Int, Int, Double?>?, // events, total, pct
    val other: Triple<Int, Int, Double?>?,
    val reportedOr: Triple<Double, Double, Double>?, // OR, CI_low, CI_high
    val rows: List<TableRow>
)

data class ExtractionCandidate(
    val status: String, // "needs_review" | "blocked"
    val counts: List<Int>?, // eventsB, totalB, eventsO, totalO
    val reasons: List<String>,
    val readings: List<TableReading>
)

// Define the job spec extension for imaging
object ImagingJobConfig {
    val EXTRACT_COUNTS_POLICY = com.example.concordance.integration.queue.WorkerRoutingPolicy.AWS_BATCH_CPU // Temporary mapping
}
