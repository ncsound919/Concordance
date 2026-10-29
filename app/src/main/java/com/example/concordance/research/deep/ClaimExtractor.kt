package com.example.concordance.research.deep

import java.util.Locale

/**
 * Rule-based extraction of structured claims from an abstract.
 *
 * This is the deterministic half of deep research. A model may *draft* a claim from these
 * fields, but the fields themselves come from regex over the text, never from generation, so
 * two runs on the same abstract produce the same claim set.
 *
 * Coverage is deliberately narrow: only the patterns below are recognised. An abstract that uses
 * a different phrasing yields no claim rather than a wrong one.
 */
object ClaimExtractor {

    data class Claim(
        val genes: List<String>,
        val drugs: List<String>,
        val diseases: List<String>,
        val endpoints: List<String>,
        val pValues: List<Double>,
        val sampleSizes: List<Int>,
        val hazardRatios: List<Double>,
        val oddsRatios: List<Double>,
        val confidence: Double,
    ) {
        val isEmpty: Boolean
            get() = genes.isEmpty() && drugs.isEmpty() && diseases.isEmpty() &&
                endpoints.isEmpty() && pValues.isEmpty() && sampleSizes.isEmpty()
    }

    private val GENES = setOf(
        "EGFR", "BRAF", "KRAS", "NRAS", "HRAS", "PIK3CA", "PTEN", "TP53", "RB1", "CDKN2A",
        "MYC", "ERBB2", "HER2", "ESR1", "ALK", "ROS1", "MET", "RET", "NTRK1", "NTRK2", "NTRK3",
        "FGFR1", "FGFR2", "FGFR3", "IDH1", "IDH2", "TERT", "ATRX", "MGMT", "BRCA1", "BRCA2",
        "PALB2", "ATM", "CHEK2", "RAD51", "PARP1", "PARP2", "VEGFA", "VEGFR", "PDGFRA",
        "PDGFRB", "KIT", "FLT3", "JAK1", "JAK2", "BCL2", "BCL6", "MYCN", "CDK4", "CDK6",
        "CCND1", "MDM2", "NF1", "NF2", "SMARCB1", "ARID1A", "ARID1B", "PBRM1", "SETD2",
        "VHL", "FBXW7", "NOTCH1", "CTNNB1", "APC", "SMAD4", "STK11", "KEAP1", "NFE2L2",
        "SOX2", "SOX9", "FOXA1", "GATA3", "ESR2", "PGR", "AR", "TMPRSS2", "ERG",
    )

    private val DRUGS = setOf(
        "ERLOTINIB", "GEFITINIB", "OSIMERTINIB", "AFATINIB", "DABRAFENIB", "TRAMETINIB",
        "VEMURAFENIB", "CRIZOTINIB", "CERITINIB", "ALECTINIB", "LORLATINIB", "ENTRECTINIB",
        "LAROTRECTINIB", "SOTORASIB", "ADAGRASIB", "CAPMATINIB", "TEPOTINIB", "SAVOLITINIB",
        "PEMBROLIZUMAB", "NIVOLUMAB", "ATEZOLIZUMAB", "DURVALUMAB", "IPILIMUMAB", "TREMELIMUMAB",
        "TRASTUZUMAB", "PERTUZUMAB", "T-DM1", "T-DXD", "LAPATINIB", "NERATINIB", "TUCATINIB",
        "PALBOCICLIB", "RIBOCICLIB", "ABEMACICLIB", "OLAPARIB", "RUCAPARIB", "NIRAPARIB",
        "TALAZOPARIB", "BEVACIZUMAB", "RAMUCIRUMAB", "SUNITINIB", "SORAFENIB", "PAZOPANIB",
        "CABOZANTINIB", "REGORAFENIB", "LENVATINIB", "AXITINIB", "EVEROLIMUS", "TEM SIROLIMUS",
        "TEM SIROLIMUS", "TMZ", "TEMOZOLOMIDE", "RADIATION", "CHEMOTHERAPY",
    )

    private val DISEASES = setOf(
        "GLIOBLASTOMA", "GBM", "BREAST CANCER", "NSCLC", "NON-SMALL CELL LUNG CANCER",
        "MELANOMA", "COLORECTAL CANCER", "PANCREATIC CANCER", "OVARIAN CANCER", "PROSTATE CANCER",
        "LUNG CANCER", "BRAIN METASTASIS", "BRAIN METASTASES", "BRAIN TUMOR", "BRAIN TUMOUR",
        "METASTATIC", "ASTROCYTOMA", "OLIGODENDROGLIOMA", "MEDULLOBLASTOMA", "Ependymoma",
        "MENINGIOMA", "RENAL CELL CARCINOMA", "HCC", "HEPATOCELLULAR CARCINOMA",
    )

    private val ENDPOINTS = setOf(
        "PFS", "OS", "ORR", "DFS", "EFS", "RFS", "TTP", "DCR", "CBR", "TTF", "TTNT",
        "PROGRESSION-FREE SURVIVAL", "OVERALL SURVIVAL", "OBJECTIVE RESPONSE RATE",
        "DISEASE-FREE SURVIVAL", "EVENT-FREE SURVIVAL",
    )

    private val P_VALUE_REGEX = Regex("""p\s*[=<>≤≥]\s*(\d+\.?\d*(?:[eE]-?\d+)?)""")
    private val HR_REGEX = Regex("""hazard ratio(?:\s+(?:was|is|of))?[\s:=]*(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val OR_REGEX = Regex("""odds ratio(?:\s+(?:was|is|of))?[\s:=]*(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val N_REGEX = Regex("""\bn\s*=\s*(\d+)""")
    private val PATIENTS_REGEX = Regex("""(\d+)\s*patients""", RegexOption.IGNORE_CASE)

    fun extract(abstract: String): Claim {
        if (abstract.isBlank()) return Claim(emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), 0.0)

        val upper = abstract.uppercase(Locale.US)

        val genes = GENES.filter { gene -> upper.contains(Regex("\\b" + Regex.escape(gene) + "\\b")) }
            .distinct().sorted()
        val drugs = DRUGS.filter { drug -> upper.contains(Regex("\\b" + Regex.escape(drug.replace(" ", "")) + "\\b")) }
            .distinct().sorted()
        val diseases = DISEASES.filter { d -> upper.contains(d) }.distinct().sorted()
        val endpoints = ENDPOINTS.filter { e -> upper.contains(e) }.distinct().sorted()

        val pValues = P_VALUE_REGEX.findAll(abstract)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .distinct().sorted().toList()
        val sampleSizes = (N_REGEX.findAll(abstract).map { it.groupValues[1].toIntOrNull() } +
            PATIENTS_REGEX.findAll(abstract).map { it.groupValues[1].toIntOrNull() })
            .filterNotNull().distinct().sorted().toList()
        val hazardRatios = HR_REGEX.findAll(abstract)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }.distinct().sorted().toList()
        val oddsRatios = OR_REGEX.findAll(abstract)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }.distinct().sorted().toList()

        // Confidence is the fraction of the four entity kinds we found, capped. It is a
        // *coverage* measure, not an accuracy measure, and must not be read as one.
        val kinds = listOf(genes, drugs, diseases, endpoints).count { it.isNotEmpty() }
        val confidence = kinds / 4.0

        return Claim(
            genes = genes,
            drugs = drugs,
            diseases = diseases,
            endpoints = endpoints,
            pValues = pValues,
            sampleSizes = sampleSizes,
            hazardRatios = hazardRatios,
            oddsRatios = oddsRatios,
            confidence = confidence,
        )
    }
}
