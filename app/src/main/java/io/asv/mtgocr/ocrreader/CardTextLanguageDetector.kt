package io.asv.mtgocr.ocrreader

import android.graphics.Bitmap
import android.os.Build
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.asv.mtgocr.ocrreader.data.CardLanguage

data class CardTextLanguageResult(
    val languageCode: String,
    val confidence: Float,
    val recognizedText: String
)

/** Reads the rules box, then identifies its language instead of inferring it from the card name. */
class CardTextLanguageDetector {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(MIN_LANGUAGE_CONFIDENCE)
                .build()
        )
    } else null

    fun detect(
        card: Bitmap,
        fallbackLanguage: String,
        callback: (CardTextLanguageResult) -> Unit
    ) {
        val fallback = CardLanguage.toCode(fallbackLanguage)
        val left = (card.width * .055f).toInt().coerceIn(0, card.width - 1)
        val top = (card.height * .59f).toInt().coerceIn(0, card.height - 1)
        val right = (card.width * .945f).toInt().coerceIn(left + 1, card.width)
        val bottom = (card.height * .89f).toInt().coerceIn(top + 1, card.height)
        val rawRulesBox = Bitmap.createBitmap(card, left, top, right - left, bottom - top)
        val scale = minOf(2f, TARGET_RULES_WIDTH / rawRulesBox.width.toFloat()).coerceAtLeast(1f)
        val rulesBox = if (scale > 1.01f) Bitmap.createScaledBitmap(
            rawRulesBox,
            (rawRulesBox.width * scale).toInt(),
            (rawRulesBox.height * scale).toInt(),
            true
        ).also { rawRulesBox.recycle() } else rawRulesBox
        textRecognizer.process(InputImage.fromBitmap(rulesBox, 0))
            .addOnSuccessListener { recognized ->
                rulesBox.recycle()
                val text = recognized.text
                    .lineSequence()
                    .map(String::trim)
                    .filter { line -> line.count(Char::isLetter) >= 3 }
                    .joinToString(" ")
                    .take(MAX_LANGUAGE_TEXT)
                val identifier = languageIdentifier
                if (identifier == null || text.count(Char::isLetter) < MIN_TEXT_LETTERS) {
                    callback(CardTextLanguageResult(fallback, 0f, text))
                    return@addOnSuccessListener
                }
                identifier.identifyPossibleLanguages(text)
                    .addOnSuccessListener { candidates ->
                        val best = candidates
                            .asSequence()
                            .map { CardLanguage.toCode(it.languageTag) to it.confidence }
                            .filter { (code, _) -> code in SUPPORTED_CARD_LANGUAGES }
                            .maxByOrNull { it.second }
                        if (best == null || best.second < MIN_ACCEPTED_CONFIDENCE) {
                            callback(CardTextLanguageResult(fallback, 0f, text))
                        } else {
                            callback(CardTextLanguageResult(best.first, best.second, text))
                        }
                    }
                    .addOnFailureListener {
                        callback(CardTextLanguageResult(fallback, 0f, text))
                    }
            }
            .addOnFailureListener {
                rulesBox.recycle()
                callback(CardTextLanguageResult(fallback, 0f, ""))
            }
    }

    fun close() {
        textRecognizer.close()
        languageIdentifier?.close()
    }

    companion object {
        private const val MIN_LANGUAGE_CONFIDENCE = .20f
        private const val MIN_ACCEPTED_CONFIDENCE = .34f
        private const val MIN_TEXT_LETTERS = 18
        private const val MAX_LANGUAGE_TEXT = 400
        private const val TARGET_RULES_WIDTH = 1_000f
        private val SUPPORTED_CARD_LANGUAGES = setOf(
            "en", "es", "fr", "de", "it", "pt", "ja", "ko", "ru", "zhs", "zht"
        )
    }
}
