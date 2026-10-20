package com.example.concordance.integration.api

/**
 * A single curated evidence item from CIViC, normalized out of the GraphQL
 * response. This is variant-level interpretation evidence (therapeutic,
 * diagnostic, prognostic, predisposing) rather than a literature record.
 */
data class CuratedVariantEvidence(
    val civicEvidenceId: Int,
    val civicUrl: String,
    val gene: String,
    val variant: String,
    val variantTypes: List<String>,
    val molecularProfile: String,
    val evidenceType: String,
    val evidenceLevel: String,
    val evidenceDirection: String,
    val significance: String,
    val evidenceRating: Int?,
    val disease: String,
    val therapies: List<String>,
    val description: String
)

/** A public cancer-genomics cohort/study listed by cBioPortal. */
data class CohortStudy(
    val studyId: String,
    val name: String,
    val cancerType: String?,
    val description: String,
    val sampleCount: Int?,
    val pmid: String?,
    val citation: String?,
    val referenceGenome: String?,
    val url: String
)

/** A somatic mutation call from a cBioPortal molecular profile. */
data class SomaticMutation(
    val studyId: String,
    val sampleId: String,
    val patientId: String,
    val entrezGeneId: Int,
    val geneSymbol: String,
    val proteinChange: String,
    val mutationType: String,
    val chr: String?,
    val startPosition: Int?,
    val endPosition: Int?,
    val referenceAllele: String?,
    val variantAllele: String?,
    val tumorAltCount: Int?,
    val tumorRefCount: Int?,
    val mutationStatus: String?,
    val validationStatus: String?,
    val molecularProfileId: String
)

internal fun CuratedVariantEvidence.toNormalizedRecord(prov: Provenance): NormalizedRecord {
    val therapiesText = if (therapies.isEmpty()) "no therapy specified" else therapies.joinToString(", ")
    return NormalizedRecord(
        externalId = "CIVIC:EID$civicEvidenceId",
        source = "CIViC",
        title = "$gene $variant — $therapiesText ($significance)",
        authors = "CIViC curated evidence",
        journal = "CIViC (Level $evidenceLevel, $evidenceType)",
        year = null,
        doi = null,
        pmid = null,
        abstractText = description,
        url = civicUrl,
        citationCount = null,
        isOpenAccess = true,
        provenance = prov
    )
}

internal fun CohortStudy.toNormalizedRecord(prov: Provenance): NormalizedRecord = NormalizedRecord(
    externalId = "cBioPortal:$studyId",
    source = "cBioPortal",
    title = name.ifBlank { studyId },
    authors = citation ?: "cBioPortal",
    journal = "cBioPortal cohort (${cancerType ?: "unspecified"})",
    year = null,
    doi = null,
    pmid = pmid,
    abstractText = description,
    url = url,
    citationCount = null,
    isOpenAccess = true,
    provenance = prov
)
