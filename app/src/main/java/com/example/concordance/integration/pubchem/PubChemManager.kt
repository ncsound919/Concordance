package com.example.concordance.integration.pubchem

import com.example.concordance.integration.rotation.ApiRotationManager
import com.example.concordance.integration.rotation.ProviderCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PubChemCompound(
    val cid: Long,
    val name: String,
    val synonyms: List<String>,
    val molecularFormula: String,
    val molecularWeight: Double,
    val iupacName: String,
    val targetGene: String,
    val drugClass: String,
    val mechanism: String,
    val bbbPermeability: String, // Blood-Brain Barrier profile
    val associatedMutations: List<String>,
    val pubchemUrl: String
)

object PubChemManager {

    private val BENCHMARK_COMPOUNDS = listOf(
        PubChemCompound(
            cid = 54726192,
            name = "Elacestrant (RAD1901)",
            synonyms = listOf("Orserdu", "RAD-1901"),
            molecularFormula = "C30H38N2O2",
            molecularWeight = 458.6,
            iupacName = "(6R)-6-(2-(N-(4-(2-(ethylamino)ethyl)benzyl)methylsulfonamido)phenyl)-5,6,7,8-tetrahydronaphthalen-2-ol",
            targetGene = "ESR1 (Estrogen Receptor 1)",
            drugClass = "Oral Selective Estrogen Receptor Degrader (SERD)",
            mechanism = "Binds to estrogen receptor-alpha, degrades the receptor protein, and inhibits downstream ER-dependent gene transcription.",
            bbbPermeability = "Low-to-moderate CNS penetration; isolated CNS progression observed with tissue-positive / plasma-negative discordance.",
            associatedMutations = listOf("ESR1 Y537S", "ESR1 D538G", "ESR1 E380Q"),
            pubchemUrl = "https://pubchem.ncbi.nlm.nih.gov/compound/54726192"
        ),
        PubChemCompound(
            cid = 104741,
            name = "Fulvestrant",
            synonyms = listOf("Faslodex", "ICI 182,780"),
            molecularFormula = "C32H47F5O3S",
            molecularWeight = 606.8,
            iupacName = "7alpha-[9-(4,4,5,5,5-pentafluoropentylsulfinyl)nonyl]estra-1,3,5(10)-triene-3,17beta-diol",
            targetGene = "ESR1 (Estrogen Receptor 1)",
            drugClass = "Intramuscular SERD (First-in-Class)",
            mechanism = "Competitive antagonist that blocks nuclear translocation and triggers rapid degradation of estrogen receptors.",
            bbbPermeability = "Extremely poor blood-brain barrier penetrance due to lipophilicity and molecular weight; high rates of sanctuary site progression.",
            associatedMutations = listOf("ESR1 Y537S", "ESR1 Y537N", "ESR1 D538G"),
            pubchemUrl = "https://pubchem.ncbi.nlm.nih.gov/compound/104741"
        ),
        PubChemCompound(
            cid = 139595188,
            name = "Camizestrant (AZD9833)",
            synonyms = listOf("AZD9833", "SERENA Lead"),
            molecularFormula = "C32H35ClF3N5O2",
            molecularWeight = 614.1,
            iupacName = "5-[(1R,3R)-2-(2-fluoro-2-methylpropyl)-3-methyl-2,3,4,9-tetrahydro-1H-pyrido[3,4-b]indol-1-yl]-N-(3-chloropyridin-2-yl)pyridine-2-carboxamide",
            targetGene = "ESR1 (Estrogen Receptor 1)",
            drugClass = "Next-Generation Oral SERD & Pure Antagonist",
            mechanism = "Potent, selective ER-alpha degrader engineered to retain complete antagonistic efficacy against constitutively active ESR1 Y537S mutants.",
            bbbPermeability = "Moderate pre-clinical CNS distribution; evaluated in active phase 2/3 metastatic trials.",
            associatedMutations = listOf("ESR1 Y537S", "ESR1 D538G"),
            pubchemUrl = "https://pubchem.ncbi.nlm.nih.gov/compound/139595188"
        ),
        PubChemCompound(
            cid = 139595210,
            name = "Giredestrant (GDC-9545)",
            synonyms = listOf("GDC-9545", "RG6173"),
            molecularFormula = "C32H35F2N5O3",
            molecularWeight = 575.7,
            iupacName = "3-[(2R)-2-[[(1R,3R)-2-(2,2-difluoroethyl)-3-methyl-2,3,4,9-tetrahydro-1H-pyrido[3,4-b]indol-1-yl]methyl]morpholin-4-yl]benzonitrile",
            targetGene = "ESR1 (Estrogen Receptor 1)",
            drugClass = "Oral SERD with Enhanced CNS Penetration",
            mechanism = "High-affinity ER degradation with complete shut-off of canonical ER signaling in wild-type and ESR1-mutated models.",
            bbbPermeability = "Documented intracranial bioavailability in animal xenografts; active clinical evaluation in CNS metastatic cohorts.",
            associatedMutations = listOf("ESR1 Y537S", "ESR1 D538G"),
            pubchemUrl = "https://pubchem.ncbi.nlm.nih.gov/compound/139595210"
        ),
        PubChemCompound(
            cid = 216416,
            name = "Lasofoxifene",
            synonyms = listOf("Oporia", "Fablyn", "CP-336,156"),
            molecularFormula = "C28H31NO2",
            molecularWeight = 413.6,
            iupacName = "(5R,6S)-6-phenyl-5-[4-(2-pyrrolidin-1-ylethoxy)phenyl]-5,6,7,8-tetrahydronaphthalen-2-ol",
            targetGene = "ESR1 (Estrogen Receptor 1)",
            drugClass = "Next-Generation Selective Estrogen Receptor Modulator (SERM)",
            mechanism = "Tissue-selective ER agonist/antagonist with unique structural positioning against ESR1 Y537S active conformation.",
            bbbPermeability = "Significant CNS tissue uptake; demonstrated intracranial antitumor activity in pre-clinical intracranial models (ELAINE-2).",
            associatedMutations = listOf("ESR1 Y537S"),
            pubchemUrl = "https://pubchem.ncbi.nlm.nih.gov/compound/216416"
        )
    )

    private val _compounds = MutableStateFlow(BENCHMARK_COMPOUNDS)
    val compounds: StateFlow<List<PubChemCompound>> = _compounds.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchCompounds(query: String): List<PubChemCompound> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return _compounds.value
        return _compounds.value.filter {
            it.name.lowercase(Locale.US).contains(q) ||
                    it.targetGene.lowercase(Locale.US).contains(q) ||
                    it.associatedMutations.any { m -> m.lowercase(Locale.US).contains(q) } ||
                    it.drugClass.lowercase(Locale.US).contains(q) ||
                    it.cid.toString().contains(q)
        }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Real PUG-REST lookup. Curated fields (target, mechanism, BBB note) are kept
     * when the CID is already in the bench; compounds that are new to the app are
     * added to the list with only the fields PubChem actually returns.
     */
    suspend fun queryPubChemPugRest(compoundName: String): PubChemCompound? = withContext(Dispatchers.IO) {
        val query = compoundName.trim()
        if (query.isEmpty()) return@withContext null

        val provider = ApiRotationManager.getNextProvider(ProviderCategory.BIOCHEM_COMPOUNDS)
        val start = System.currentTimeMillis()
        _isSearching.value = true
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url(
                    "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/" +
                        "$encoded/property/MolecularFormula,MolecularWeight,IUPACName/JSON"
                )
                .header("User-Agent", "Concordance/1.0.1 (research tool; contact: ncsound919)")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val properties = JSONObject(body)
                    .getJSONObject("PropertyTable")
                    .getJSONArray("Properties")
                    .getJSONObject(0)

                val cid = properties.optLong("CID")
                if (cid <= 0L) return@withContext null
                val formula = properties.optString("MolecularFormula")
                val weight = properties.optDouble("MolecularWeight", 0.0)
                val iupac = properties.optString("IUPACName")
                val curated = _compounds.value.firstOrNull { it.cid == cid }

                val compound = if (curated != null) {
                    curated.copy(
                        molecularFormula = formula.ifBlank { curated.molecularFormula },
                        molecularWeight = if (weight > 0.0) weight else curated.molecularWeight,
                        iupacName = iupac.ifBlank { curated.iupacName }
                    )
                } else {
                    PubChemCompound(
                        cid = cid,
                        name = query,
                        synonyms = emptyList(),
                        molecularFormula = formula,
                        molecularWeight = weight,
                        iupacName = iupac,
                        targetGene = "",
                        drugClass = "PubChem record",
                        mechanism = "",
                        bbbPermeability = "Not characterised in this app",
                        associatedMutations = emptyList(),
                        pubchemUrl = "https://pubchem.ncbi.nlm.nih.gov/compound/$cid"
                    )
                }

                if (curated == null) {
                    _compounds.value = listOf(compound) + _compounds.value
                }
                ApiRotationManager.recordSuccess(provider.id, System.currentTimeMillis() - start)
                return@withContext compound
            }
        } catch (_: Exception) {
            return@withContext null
        } finally {
            _isSearching.value = false
        }
    }
}
