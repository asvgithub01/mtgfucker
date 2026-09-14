package io.asv.mtgocr.ocrreader

/** Chooses the physical card language, favoring evidence that cannot be ordinary rules text. */
object CardLanguageEvidenceResolver {
    private const val RELIABLE_RULES_LANGUAGE_CONFIDENCE = .50f

    fun resolve(
        footerLanguage: String?,
        detectedRulesLanguage: String?,
        detectedRulesConfidence: Float,
        matchedTitleLanguage: String = ""
    ): String {
        val localizedTitle = matchedTitleLanguage
            .takeIf { it.isNotBlank() && it != "en" }
            .orEmpty()
        val reliableRulesLanguage = detectedRulesLanguage
            .takeIf { !it.isNullOrBlank() && detectedRulesConfidence >= RELIABLE_RULES_LANGUAGE_CONFIDENCE }
            .orEmpty()

        return localizedTitle
            .ifBlank { reliableRulesLanguage }
            .ifBlank { footerLanguage.orEmpty() }
            .ifBlank { detectedRulesLanguage.orEmpty() }
            .ifBlank { matchedTitleLanguage }
    }
}
