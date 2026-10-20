package com.example.concordance.local

import android.content.Context
import java.io.File

/**
 * On-device model layout.
 *
 * Weights are deliberately **not** bundled in the APK (they are 2-6 GB). They live in the
 * app's external files directory, which needs no storage permission and survives updates:
 *
 *   <externalFilesDir>/models/<name>.gguf      local chat / reasoning model
 *   <externalFilesDir>/kokoro/model.onnx       Kokoro acoustic model
 *   <externalFilesDir>/kokoro/voices.bin       voice style table (54 voices)
 *   <externalFilesDir>/kokoro/tokens.txt       phoneme token table
 *   <externalFilesDir>/kokoro/espeak-ng-data   in-process G2P data
 *
 * Nothing here performs network I/O.
 */
object LocalModels {

    /** Loopback port used by the embedded llama.cpp server. */
    const val LLM_PORT = 8080

    /** Kokoro speaker id for `bm_lewis` (British English male narrator). */
    const val DEFAULT_VOICE_ID = 27
    const val DEFAULT_VOICE_NAME = "bm_lewis"

    /** Preferred model when several are installed. */
    private val PREFERRED_PREFIXES = listOf("Qwen3.8", "medgemma", "Qwen3.5", "Qwen3-", "gemma-4")

    fun modelsDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "models")

    fun kokoroDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "kokoro")

    /** Installed GGUF files, preferred model first. */
    fun installedModels(context: Context): List<File> {
        val dir = modelsDir(context)
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("gguf", true) }
            ?.toList()
            .orEmpty()
        android.util.Log.i(
            "LocalModels",
            "modelsDir=${dir.absolutePath} isDir=${dir.isDirectory} canRead=${dir.canRead()} " +
                "found=${files.size} ${files.joinToString { it.name }}"
        )
        return files.sortedWith(
            compareBy<File> { file ->
                val index = PREFERRED_PREFIXES.indexOfFirst { file.name.startsWith(it, ignoreCase = true) }
                if (index < 0) PREFERRED_PREFIXES.size else index
            }.thenBy { it.name }
        )
    }

    fun defaultModel(context: Context): File? = installedModels(context).firstOrNull()

    /** True only when every file Kokoro needs is present. */
    fun kokoroReady(context: Context): Boolean {
        val dir = kokoroDir(context)
        return File(dir, "model.onnx").isFile &&
            File(dir, "voices.bin").isFile &&
            File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory
    }

    fun runtimeBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libllamaserver.so")

    fun runtimeReady(context: Context): Boolean = runtimeBinary(context).isFile

    /** Human-readable size, e.g. "2.32 GB". */
    fun describe(file: File): String {
        val gb = file.length() / 1_073_741_824.0
        return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", file.length() / 1_048_576.0)
    }
}
