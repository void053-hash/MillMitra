package com.millmitra.app.util

/**
 * Utility to sanitize AI outputs, stripping raw markdown asterisks, hashes,
 * and normalizing bullet points into clean, high-contrast industrial text.
 */
object TextSanitizer {

    data class ParsedReport(
        val reportText: String,
        val vocabText: String?
    )

    fun splitReportAndVocab(rawText: String?): ParsedReport {
        if (rawText.isNullOrBlank()) return ParsedReport("", null)

        val parts = rawText.split(Regex("(?i)\\[vocab\\]"))
        val reportText = sanitizeForDisplay(parts[0])
        val vocabText = if (parts.size > 1 && parts[1].isNotBlank()) {
            sanitizeForDisplay(parts[1])
        } else null

        return ParsedReport(reportText, vocabText)
    }

    fun sanitizeForDisplay(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        return rawText
            // Replace markdown list bullets (* or - at start of line) with standard bullet symbol (•)
            .replace(Regex("(?m)^\\s*[*\\-]\\s+"), "• ")
            // Clean markdown bold (**text**) -> text
            .replace(Regex("\\*\\*(.*?)\\*\\*")) { it.groupValues[1] }
            // Clean markdown italics (*text*) -> text
            .replace(Regex("\\*(.*?)\\*")) { it.groupValues[1] }
            // Clean markdown headers (# Header) -> Header
            .replace(Regex("(?m)^#+\\s*"), "")
            // Clean backticks
            .replace("`", "")
            // Normalize excessive blank lines (more than 2 newlines -> 2 newlines)
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
