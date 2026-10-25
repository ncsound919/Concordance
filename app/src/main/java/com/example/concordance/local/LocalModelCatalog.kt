package com.example.concordance.local

/**
 * A GGUF model the app can fetch itself.
 *
 * Only publisher-hosted files belong here: the URL, byte count and SHA-256 are
 * compile-time constants so a tampered download server cannot swap in different
 * weights. Anything the operator copies in by hand still shows up in
 * [LocalModels.installedModels]; this catalog only powers the download buttons.
 */
data class InstallableModel(
    val id: String,
    val fileName: String,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,
    val sha256: String,
    val url: String,
    val source: String,
    val license: String
)

object LocalModelCatalog {

    /**
     * Default small tier. Matches the file that was verified on the S25:
     * extraction in ~11 s, typed decision in ~2-6 s, no reasoning leak.
     */
    val QWEN_4B = InstallableModel(
        id = "qwen3.5-4b-q4_k_m",
        fileName = "Qwen3.5-4B-Q4_K_M.gguf",
        displayName = "Qwen3.5 4B (Q4_K_M)",
        description = "Default small tier: paper extraction, drafting, and the decision gate's second opinion.",
        sizeBytes = 2_740_937_888L,
        sha256 = "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
        url = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
        source = "Hugging Face · unsloth/Qwen3.5-4B-GGUF",
        license = "Apache-2.0"
    )

    /**
     * Light tier. Useful when the 4B is too slow or the phone is low on RAM;
     * it is a chat/extraction model, not a substitute for checking claims.
     */
    val QWEN_0_8B = InstallableModel(
        id = "qwen3.5-0.8b-q4_k_m",
        fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
        displayName = "Qwen3.5 0.8B (Q4_K_M)",
        description = "Fast, low-memory tier for quick local checks when the 4B is too slow or RAM is tight.",
        sizeBytes = 532_517_120L,
        sha256 = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517",
        url = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
        source = "Hugging Face · unsloth/Qwen3.5-0.8B-GGUF",
        license = "Apache-2.0"
    )

    /** Offered in the BYOK screen, in preference order. */
    val entries: List<InstallableModel> = listOf(QWEN_4B, QWEN_0_8B)

    fun forFileName(fileName: String): InstallableModel? =
        entries.firstOrNull { it.fileName == fileName }

    fun forId(id: String): InstallableModel? = entries.firstOrNull { it.id == id }
}
