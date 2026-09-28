package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class LanguageReviewPolicyTest {
    private fun guess(code: String, confidence: Double, length: Int=80) = LanguageReviewPolicy.Guess(code,confidence,length)
    @Test fun proposesStrongPortugueseOnly() { assertEquals("pt",LanguageReviewPolicy.propose(listOf(guess("pt",.95),guess("es",.5)))) }
    @Test fun ambiguousOrShortReadingsDoNotSetLanguage() {
        assertNull(LanguageReviewPolicy.propose(listOf(guess("pt",.9),guess("es",.85))))
        assertNull(LanguageReviewPolicy.propose(listOf(guess("en",.99,8))))
        assertNull(LanguageReviewPolicy.propose(listOf(guess("pt",.7))))
    }
    @Test fun russianChineseAndInvalidScoresStayForManualReview() {
        for(code in listOf("ru","zh","zhs","zht","und")) assertNull(LanguageReviewPolicy.propose(listOf(guess(code,.99))))
        assertNull(LanguageReviewPolicy.propose(listOf(guess("es",Double.NaN))))
    }
    @Test fun repeatedSameLanguageDoesNotCreateFalseRunner() { assertEquals("ja",LanguageReviewPolicy.propose(listOf(guess("ja",.92),guess("ja",.91)))) }
    @Test fun rawFrameOwnsPixelsBeforeCameraReuse() {
        val raw=byteArrayOf(1,2,3)
        val frame=OcrFramePhoto.snapshot(42,raw,1,2,3)
        raw[0]=99
        assertEquals(42,frame.id); assertEquals(1,frame.bytes[0].toInt()); assertEquals(3,frame.rotation)
    }
}
