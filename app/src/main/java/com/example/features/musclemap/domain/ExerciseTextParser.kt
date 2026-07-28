package com.example.features.musclemap.domain

import com.example.data.model.normalizeExerciseSearchText

data class ExerciseNameCandidates(
    val whole: String,
    val commaFallbacks: List<String>
) {
    val allKeys: List<String>
        get() = listOf(whole) + commaFallbacks
}

/** Parses persisted workout text without treating every comma as an exercise separator. */
object ExerciseTextParser {
    private val fragmentSeparator = Regex("[;\\r\\n]+")
    private val unambiguousListPrefix = Regex("""^\s*(?:[-*•▪◦]\s+|\d{1,3}[.)]\s+)""")
    private val currentFormatName = Regex(
        """^(.+?):\s*\d+(?:[.,]\d+)?\s*(?:(?:series?)\b|[x×]\s*\d+\b)""",
        RegexOption.IGNORE_CASE
    )
    private val compactSetRepSuffix = Regex(
        """^(.*?)\s+\d+(?:[.,]\d+)?\s*[x×]\s*\d+(?:[.,]\d+)?(?:\s*@.*)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(exercisesText: String): List<ExerciseNameCandidates> =
        exercisesText.split(fragmentSeparator).mapNotNull(::parseFragment)

    private fun parseFragment(rawFragment: String): ExerciseNameCandidates? {
        val fragment = rawFragment.replaceFirst(unambiguousListPrefix, "").trim()
        if (fragment.isEmpty()) return null

        val extracted = extractCurrentName(fragment)
        val whole = normalizeCandidate(extracted.name) ?: return null
        val commaFallbacks = if (extracted.hasPrescription || ',' !in fragment) {
            emptyList()
        } else {
            fragment.split(',').mapNotNull { part ->
                normalizeCandidate(part.replaceFirst(unambiguousListPrefix, ""))
            }
        }

        return ExerciseNameCandidates(whole = whole, commaFallbacks = commaFallbacks)
    }

    private fun extractCurrentName(fragment: String): ExtractedName {
        currentFormatName.find(fragment)?.groupValues?.getOrNull(1)?.let {
            return ExtractedName(name = it, hasPrescription = true)
        }
        compactSetRepSuffix.matchEntire(fragment)?.groupValues?.getOrNull(1)?.let {
            return ExtractedName(name = it, hasPrescription = true)
        }
        return ExtractedName(name = fragment, hasPrescription = false)
    }

    private fun normalizeCandidate(value: String): String? =
        normalizeExerciseSearchText(value).ifBlank { null }

    private data class ExtractedName(
        val name: String,
        val hasPrescription: Boolean
    )
}
