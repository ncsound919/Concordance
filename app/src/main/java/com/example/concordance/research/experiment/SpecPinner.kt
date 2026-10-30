package com.example.concordance.research.experiment

import com.example.concordance.evidence.DatasetRecord
import com.example.concordance.evidence.DerivedKind
import com.example.concordance.evidence.EvidenceStore
import com.example.concordance.evidence.LineageEdge

/**
 * Pins a dataset into a spec by reference, producing a new spec version.
 *
 * The pin is an explicit, auditable operation: it resolves a [DatasetRecord] from the store and
 * writes its checksum into the spec's [DatasetRef]. After pinning, the spec carries the hash
 * directly, so the compiler and the catalog stay pure (they never touch a store).
 *
 * Pinning is the only way a spec moves from "dataset not pinned" to "ready": it is an action,
 * not an edit to a JSON file.
 */
object SpecPinner {

    sealed interface PinResult {
        data class Pinned(val spec: ExperimentSpec, val lineage: List<LineageEdge>) : PinResult
        data class Refused(val code: String, val reason: String) : PinResult
    }

    /**
     * Pins every unpinned dataset in [spec] that references a store record. Returns the new spec
     * and one lineage edge per pinned dataset. If any dataset cannot be pinned, the whole
     * operation is refused and no partial spec is produced.
     */
    fun pin(
        spec: ExperimentSpec,
        store: EvidenceStore,
        atUtc: String,
    ): PinResult {
        val targets = spec.datasets.filter { it.sha256.isBlank() && it.datasetId != null }
        if (targets.isEmpty()) {
            return PinResult.Refused("NO_DATASET_TO_PIN", "no dataset in the spec references a store record")
        }

        val pinned = spec.datasets.toMutableList()
        val lineageEdges = mutableListOf<LineageEdge>()

        for (ds in spec.datasets) {
            if (ds.sha256.isNotBlank() || ds.datasetId == null) continue
            val record = store.dataset(ds.datasetId)
                ?: return PinResult.Refused("DATASET_NOT_FOUND", "no dataset '${ds.datasetId}' in the store")
            if (!record.isPinned) {
                return PinResult.Refused("DATASET_UNPINNED", "dataset '${record.datasetId}' has no checksum")
            }
            if (!record.dataClass.mayLeaveHost && (ds.access == DataAccess.PUBLIC || ds.access == DataAccess.UNKNOWN)) {
                return PinResult.Refused("DATASET_CLASS", "dataset '${record.datasetId}' is ${record.dataClass.wireValue} and cannot be attached to a public spec")
            }
            val index = pinned.indexOf(ds)
            pinned[index] = ds.copy(
                ref = record.studyId.ifBlank { record.source },
                version = record.datasetId,
                sha256 = record.checksum,
                license = record.license,
                access = record.access,
                dataClass = record.dataClass,
                datasetId = record.datasetId,
            )
            lineageEdges += LineageEdge(
                derivedKind = DerivedKind.DATASET,
                derivedId = spec.id,
                artifactId = record.datasetId,
                transform = "dataset_pin",
                transformVersion = "1",
                createdAtUtc = atUtc,
            )
        }

        val newSpec = spec.copy(
            datasets = pinned,
            version = spec.version + 1,
        )

        return PinResult.Pinned(newSpec, lineageEdges)
    }

    /** Removes the pin from any dataset whose [datasetId] matches. */
    fun unpin(spec: ExperimentSpec, datasetId: String): ExperimentSpec {
        val unpinned = spec.datasets.map { ds ->
            if (ds.datasetId != datasetId) ds
            else ds.copy(sha256 = "", version = "", datasetId = null)
        }
        return spec.copy(datasets = unpinned, version = spec.version + 1)
    }
}
