package com.example.concordance.integration.api

import com.example.concordance.integration.rotation.LicenseClass
import com.example.concordance.integration.rotation.ProviderCategory
import org.json.JSONObject
import java.net.URLEncoder

/**
 * ChEMBL web services — molecule lookup and mechanisms of action. Keyless.
 *
 * Deep flow for mechanism claims: normalize a drug mention to its ChEMBL id,
 * then pull the MOA records so adjudication can check "does this drug actually
 * hit the claimed target". Docs: https://www.ebi.ac.uk/chembl/ws
 */
data class MoleculeRecord(
    val chemblId: String,
    val prefName: String,
    val moleculeType: String?,
    val maxPhase: Int?,
    val molecularFormula: String?
)

data class DrugMechanism(
    val moleculeChEMBLId: String,
    val mechanism: String,
    val actionType: String?,
    val targetChEMBLId: String?,
    val targetName: String?
)

object ChemblAdapter : IntegrationAdapter {

    private const val BASE = "https://www.ebi.ac.uk/chembl/api/data"

    override val descriptor = AdapterDescriptor(
        id = "chembl_api",
        displayName = "ChEMBL Drug Targets",
        category = ProviderCategory.BIOCHEM_COMPOUNDS,
        authType = AuthType.NONE,
        licenseClass = LicenseClass.OPEN,
        rateLimit = RateLimitSpec(requestsPerSecond = 1.0, burst = 2),
        capabilities = setOf(Capability.SEARCH, Capability.FETCH_METADATA),
        docsUrl = "https://www.ebi.ac.uk/chembl/ws"
    )

    override suspend fun search(query: IntegrationQuery): List<NormalizedRecord> {
        val term = query.text.trim()
        if (term.isBlank()) return emptyList()
        val (molecules, prov) = fetchMolecules(term, query.pageSize) ?: return emptyList()
        return molecules.map { m ->
            NormalizedRecord(
                externalId = "ChEMBL:${m.chemblId}",
                source = "ChEMBL",
                title = m.prefName.ifBlank { m.chemblId },
                authors = "ChEMBL",
                journal = "ChEMBL molecule (${m.moleculeType ?: "unknown type"})",
                year = null,
                doi = null,
                pmid = null,
                abstractText = "",
                url = "https://www.ebi.ac.uk/chembl/compound_report_card/${m.chemblId}/",
                citationCount = null,
                isOpenAccess = true,
                provenance = prov
            )
        }
    }

    /** Molecules whose name matches [term] (fuzzy server-side `q` search). */
    suspend fun fetchMolecules(term: String, limit: Int = 25): Pair<List<MoleculeRecord>, Provenance>? {
        val t = term.trim()
        if (t.isBlank()) return null
        val encoded = URLEncoder.encode(t, "UTF-8")
        val pageSize = limit.coerceIn(1, 100)
        val url = "$BASE/molecule.json?q=$encoded&limit=$pageSize"

        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = t,
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return null
        return parseMolecules(resp.body) to resp.provenance
    }

    /** Mechanism-of-action records for a molecule (e.g. "EGFR inhibitor"). */
    suspend fun fetchMechanisms(chemblId: String): List<DrugMechanism> {
        val id = chemblId.trim()
        if (id.isBlank()) return emptyList()
        val url = "$BASE/mechanism_of_action.json?molecule_chembl_id=$id"
        val resp = HttpJsonClient.getJson(
            providerId = descriptor.id,
            url = url,
            queryLabel = "moa:$id",
            spec = descriptor.rateLimit,
            licenseClass = descriptor.licenseClass
        ) ?: return emptyList()
        return parseMechanisms(resp.body)
    }

    /** Resolves a drug name to its canonical ChEMBL id (first match). */
    suspend fun resolveChemblId(drugName: String): String? =
        fetchMolecules(drugName, 5)?.first?.firstOrNull { it.chemblId.isNotBlank() }?.chemblId

    internal fun parseMolecules(json: String): List<MoleculeRecord> = try {
        val arr = JSONObject(json).optJSONArray("molecules") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("molecule_chembl_id")
            if (id.isBlank()) return@mapNotNull null
            MoleculeRecord(
                chemblId = id,
                prefName = o.optString("pref_name"),
                moleculeType = o.optString("molecule_type").ifBlank { null },
                maxPhase = o.optInt("max_phase", -1).takeIf { it >= 0 },
                molecularFormula = o.optJSONObject("molecule_properties")
                    ?.optString("full_molformula")?.ifBlank { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    internal fun parseMechanisms(json: String): List<DrugMechanism> = try {
        val arr = JSONObject(json).optJSONArray("mechanism_of_actions") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mech = o.optString("mechanism_of_action")
            if (mech.isBlank()) return@mapNotNull null
            DrugMechanism(
                moleculeChEMBLId = o.optString("molecule_chembl_id"),
                mechanism = mech,
                actionType = o.optString("action_type").ifBlank { null },
                targetChEMBLId = o.optString("target_chembl_id").ifBlank { null },
                targetName = o.optString("target_pref_name").ifBlank { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
