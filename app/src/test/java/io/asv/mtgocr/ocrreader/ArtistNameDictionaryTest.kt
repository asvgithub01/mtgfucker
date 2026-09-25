package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class ArtistNameDictionaryTest {
    private fun observation(value: String, pass: Int) = FooterObservation(value,
        PrintingOcrLine(pass, value, .1f, .92f, .5f, .94f))
    private fun normalize(vararg names: String, dictionary: List<String> = listOf("Jason Chan", "Wayne England")) =
        ArtistNameDictionary(dictionary).normalize(FooterField(names.mapIndexed { i, s -> observation(s, i) }))
    @Test fun strayLetterNeedsExactOtherPassAndPreservesRaw() {
        val read = normalize("Jason Chan", "z Jason Chan")
        assertEquals(ScanReadState.READ, read.state)
        assertEquals(listOf("Jason Chan"), read.values)
        assertEquals("z Jason Chan", read.observations[1].source.text)
        assertEquals("z Jason Chan", read.observations[1].originalValue)
        assertEquals("ARTIST_DICTIONARY_CORROBORATED", read.observations[1].normalization)
    }
    @Test fun uncorroboratedTypoIsNotGuessed() {
        assertEquals(listOf("z Jason Chan"), normalize("z Jason Chan").values)
        assertEquals(listOf("Jason Chon"), normalize("Jason Chon").values)
    }
    @Test fun singleEditCanBeCorroborated() {
        for (typo in listOf("Jason Chon", "Jason Chann", "Jason Cha"))
            assertEquals(listOf("Jason Chan"), normalize("Jason Chan", typo).values)
    }
    @Test fun twoEditsAndDistantNamesRemainConflicting() {
        assertEquals(ScanReadState.CONFLICT, normalize("Jason Chan", "Jaxon Chon").state)
        assertEquals(ScanReadState.CONFLICT, normalize("Jason Chan", "Wayne England").state)
    }
    @Test fun ambiguousDictionaryDoesNotUseCorroborationToPickWinner() {
        val read = normalize("Jason Chan", "Jason Chon", dictionary = listOf("Jason Chan", "Jason Chin"))
        assertEquals(ScanReadState.CONFLICT, read.state)
    }
    @Test fun samePassCannotCorroborate() {
        val field = FooterField(listOf(observation("Jason Chan", 1), observation("z Jason Chan", 1)))
        assertEquals(field, ArtistNameDictionary(listOf("Jason Chan")).normalize(field))
    }
    @Test fun exactNamesNeverCorrectedToAnotherKnownArtist() {
        val read = normalize("Jason Chan", "z Jason Chan", dictionary = listOf("Jason Chan", "z Jason Chan"))
        assertEquals(ScanReadState.CONFLICT, read.state)
    }
    @Test fun normalizationIsIdempotentAndEmptyVocabularyPreservesEvidence() {
        val field = FooterField(listOf(observation("JÁSON  CHAN", 1), observation("z Jason Chan", 2)))
        val dictionary = ArtistNameDictionary(listOf("Jason Chan"))
        val result = dictionary.normalize(field)
        assertEquals(listOf("Jason Chan"), result.values)
        assertEquals(result, dictionary.normalize(result))
        assertEquals(field, ArtistNameDictionary(emptyList()).normalize(field))
    }
    @Test fun normalizedCollisionsStayUnresolved() {
        assertEquals(listOf("JASON CHAN"), normalize("JASON CHAN", dictionary = listOf("Jason Chan", "Jasón Chan")).values)
    }
}
