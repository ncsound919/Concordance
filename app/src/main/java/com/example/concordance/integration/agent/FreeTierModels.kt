package com.example.concordance.integration.agent

/**
 * Curated free-tier model IDs per provider, refreshed 2026-10. These rotate
 * often (especially OpenRouter/OpenCode stealth models), so the UI also allows
 * typing a custom model name. Tap a suggestion to populate the field.
 */
object FreeTierModels {

    /** OpenRouter free models (suffix :free). Auto-router picks the best available. */
    val openrouter = listOf(
        "openrouter/free",
        "nvidia/nemotron-3-super-120b-a12b:free",
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
        "poolside/laguna-s-2.1:free",
        "poolside/laguna-xs-2.1:free",
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "liquid/lfm-2.5-2.6b:free",
        "nvidia/nemotron-3.5-content-safety:free"
    )

    /** Gemini free tier (AI Studio, no credit card). */
    val gemini = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-3-flash",
        "gemini-3.1-flash-lite",
        "gemma-4-26b-a4b"
    )

    /** Mistral free experiment tier (rate-limited, all models available). */
    val mistral = listOf(
        "mistral-small-latest",
        "mistral-nemo",
        "ministral-8b-latest",
        "ministral-3b-latest",
        "codestral-latest",
        "mistral-medium-latest"
    )

    /** NVIDIA NIM free endpoints (OpenAI-compatible). */
    val nvidia = listOf(
        "deepseek-ai/deepseek-v4.1-flash",
        "moonshotai/kimi-k2.5",
        "nvidia/nemotron-3.5-lightning-30b-a3b",
        "nvidia/nemotron-3-ultra-550b-a55b",
        "nvidia/nemotron-3-super-120b-a12b",
        "meta/llama-3.3-70b-instruct",
        "meta/llama-4-maverick-17b-128e-instruct",
        "qwen/qwen3-coder-480b-a35b-instruct",
        "mistralai/mistral-large-3-675b-instruct-2512",
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b"
    )

    /** Ollama local models (pull with `ollama pull <name>`). */
    val ollama = listOf(
        "llama3.3",
        "llama3.2",
        "mistral",
        "gemma3",
        "qwen2.5",
        "deepseek-r1",
        "phi4",
        "codellama"
    )

    /** OpenCode free/stealth models rotate frequently — type the current ID. */
    val opencode = listOf(
        "opencode/big-pickle"
    )

    /** All suggestions flattened, for a single chip row. */
    fun allFor(provider: String): List<String> = when (provider.lowercase()) {
        "openrouter" -> openrouter
        "gemini" -> gemini
        "mistral" -> mistral
        "nvidia" -> nvidia
        "ollama" -> ollama
        "opencode" -> opencode
        else -> emptyList()
    }
}
