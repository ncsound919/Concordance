package com.example

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the design-system contract in-process (no Python/CI script needed):
 *  - no literal fontSize in UI screens/components
 *  - no private SectionCard duplicates
 */
class UiTokenGuardTest {

    private val roots = listOf(
        File("src/main/java/com/example/concordance/ui/screens"),
        File("src/main/java/com/example/concordance/ui/components"),
    )

    private val fontSize = Regex("""fontSize\s*=\s*\d+\.sp""")
    private val privateSectionCard = Regex("""private\s+fun\s+SectionCard\s*\(""")

    @Test
    fun ui_uses_semantic_text_styles() {
        val offenders = mutableListOf<String>()
        for (root in roots) {
            assertTrue("UI directory missing: ${root.absolutePath}", root.exists())
            root.listFiles { f -> f.extension == "kt" }?.forEach { file ->
                file.readLines().forEachIndexed { i, line ->
                    if (fontSize.containsMatchIn(line)) {
                        offenders += "${file.name}:${i + 1}: ${line.trim()}"
                    }
                }
            }
        }
        assertEquals("Literal fontSize found; use MaterialTheme.concordance.*:\n" + offenders.joinToString("\n"), 0, offenders.size)
    }

    @Test
    fun no_duplicate_section_card() {
        val offenders = mutableListOf<String>()
        for (root in roots) {
            root.listFiles { f -> f.extension == "kt" }?.forEach { file ->
                file.readLines().forEachIndexed { i, line ->
                    if (privateSectionCard.containsMatchIn(line)) {
                        offenders += "${file.name}:${i + 1}"
                    }
                }
            }
        }
        assertEquals("Private SectionCard found; use AppSectionCard:\n" + offenders.joinToString("\n"), 0, offenders.size)
    }
}
