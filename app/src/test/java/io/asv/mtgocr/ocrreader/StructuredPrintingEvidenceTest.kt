package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class StructuredPrintingEvidenceTest {
    private fun line(text: String, top: Float = .94f, pass: Int = 0, left: Float = .04f) =
        PrintingOcrLine(pass, text, left, top, left + .24f, top + .018f)
    private fun read(vararg lines: PrintingOcrLine) = StructuredPrintingEvidence.read(lines.toList(), setOf("M15", "WOE", "SPG"))

    @Test fun alignedM15FooterRetainsTuple() {
        val result = read(line("123/269 R", .91f), line("M15 • EN"))
        assertEquals(ScanReadState.READ, result.state)
        assertEquals(PrintedFooter("M15", "123", "en"), result.footer)
    }
    @Test fun recentRarityPrefixAndSuffixArePreserved() {
        assertEquals("12A", read(line("R 0012a", .91f), line("WOE EN")).footer?.number)
    }
    @Test fun setWithoutNumberIsStillOnlySetAndLanguage() {
        assertNull(read(line("SPG ES")).footer?.number)
        assertEquals("es", read(line("SPG ES")).footer?.language)
    }
    @Test fun cannotAssembleNumberAcrossProcessingPasses() {
        assertNull(read(line("123", .91f, 1), line("M15 EN", pass = 2)).footer?.number)
    }
    @Test fun distantOrRightAlignedNumberDoesNotJoinFooter() {
        assertNull(read(line("123", .81f), line("10/10", .91f, left = .72f), line("WOE EN")).footer?.number)
    }
    @Test fun rulesTextAndTopOfCardCannotImpersonateFooter() {
        assertEquals(ScanReadState.UNREADABLE, read(line("WOE EN", .5f)).state)
        val partial = read(line("Draw WOE EN"), line("2023 Wizards"))
        assertEquals(ScanReadState.PARTIAL, partial.state)
        assertNull(partial.footer)
        assertTrue(partial.candidates.isEmpty())
    }
    @Test fun emptyIsUnreadableNotObservedAbsent() {
        assertEquals(ScanReadState.UNREADABLE, read().state)
    }
    @Test fun unknownCodeAndUnknownLanguageAreNotGuessed() {
        assertEquals(ScanReadState.UNREADABLE, read(line("W0E EN")).state)
        assertEquals(ScanReadState.UNREADABLE, read(line("WOE XX")).state)
    }
    @Test fun contradictoryPassesAreConflictNotMajorityVote() {
        val result = read(line("WOE EN", pass = 0), line("WOE EN", pass = 1), line("WOE ES", pass = 2))
        assertEquals(ScanReadState.CONFLICT, result.state)
        assertNull(result.footer)
    }
    @Test fun identicalContrastPassesRemainOneTuple() {
        assertEquals(1, read(line("WOE EN"), line("WOE EN", pass = 1)).candidates.size)
    }
    @Test fun invalidGeometryIsNotEvidence() {
        assertEquals(ScanReadState.UNREADABLE, read(line("WOE EN").copy(left = Float.NaN)).state)
        assertEquals(ScanReadState.UNREADABLE, read(line("WOE EN").copy(bottom = 1.1f)).state)
    }
    @Test fun twoNumbersInSameBandRemainConflict() {
        assertEquals(ScanReadState.CONFLICT, read(line("123", .91f), line("124", .91f), line("WOE EN")).state)
    }
    @Test fun completeTupleSupersedesIdenticalIncompleteTupleOnly() {
        val result = read(line("123", .91f), line("WOE EN"), line("WOE EN", pass = 1))
        assertEquals(PrintedFooter("WOE", "123", "en"), result.footer)
    }

    private fun words(text: String, top: Float = .94f, pass: Int = 0): PrintingOcrLine {
        var x = .04f
        val elements = text.split(" ").map { word ->
            val left = x; x += .035f
            PrintingOcrElement(word, left, top, x - .005f, top + .018f)
        }
        return PrintingOcrLine(pass, text, .04f, top, x, top + .018f, elements)
    }
    @Test fun realWordBoundariesSeparateArtistFromSetAndLanguage() {
        val result = read(words("WOE EN Alex Stone"), words("U0052", .91f))
        assertEquals(PrintedFooter("WOE", "52", "en"), result.footer)
    }
    @Test fun mergedTextWithoutWordBoundariesIsNotSplitByGuessing() {
        assertEquals(PrintedFooter("WOE", null, "en"), read(words("WOEEN Alex Stone")).footer)
        assertEquals(ScanReadState.UNREADABLE, read(line("WOE EN Alex Stone")).state)
    }
    @Test fun prefixesCannotStartInMiddleOfRulesText() {
        assertEquals(ScanReadState.UNREADABLE, read(words("Draw WOE EN"), words("U0052", .91f)).state)
    }
    @Test fun rarityAndNumberCanBeAttachedButLetterOIsNotBlindlyRepaired() {
        assertEquals("150", read(line("M0150", .91f), line("SPG SP")).footer?.number)
        assertEquals("150", read(line("MO150", .91f), line("SPG SP")).footer?.number)
    }
    @Test fun zeroPaddingDoesNotCreateFakeConflict() {
        val result = read(line("0150", .91f), line("SPG SP"), line("150", .91f, 3), line("SPG SP", pass = 3))
        assertEquals(ScanReadState.READ, result.state)
        assertTrue(result.corroboratedTuple)
        assertEquals(setOf(0, 3), result.completeTuplePasses)
    }
    @Test fun threeContrastsOfSameCropDoNotQualifyAsTwoCropFamilies() {
        val result = read(line("0150", .91f), line("SPG SP"), line("0150", .91f, 1), line("SPG SP", pass = 1))
        assertFalse(result.corroboratedTuple)
    }
    @Test fun incompleteReadingInSecondCropDoesNotCorroborateNumber() {
        val result = read(line("150", .91f), line("SPG SP"), line("SPG SP", pass = 3))
        assertFalse(result.corroboratedTuple)
        assertEquals(setOf(0), result.completeTuplePasses)
    }
    @Test fun incoherentWordBoxesCannotCreateAReadablePrefix() {
        val raw = words("WOE EN Alex Stone")
        assertEquals(ScanReadState.UNREADABLE, read(raw.copy(elements = raw.elements.map { it.copy(left = Float.NaN) })).state)
        assertEquals(ScanReadState.UNREADABLE, read(raw.copy(elements = raw.elements.map { it.copy(top = .2f) })).state)
    }
    @Test fun aDistantLanguageTokenIsNotJoinedToSet() {
        val raw = words("WOE EN Alex Stone")
        assertEquals(ScanReadState.UNREADABLE, read(raw.copy(right = 1f,
            elements = raw.elements.mapIndexed { i, e -> if (i == 0) e else e.copy(left = e.left + .3f, right = e.right + .3f) })).state)
    }
    @Test fun splitDigitsOrSuffixCannotBeSilentlyTruncated() {
        assertNull(read(words("M0 150", .91f), line("SPG SP")).footer?.number)
        assertNull(read(words("150 A", .91f), line("SPG SP")).footer?.number)
        assertEquals("150", read(words("150 / 269 R", .91f), line("SPG SP")).footer?.number)
    }
    @Test fun numericConfusionsAreContextualAndSuffixesSurvive() {
        assertEquals("57", read(line("O57/272 C", .91f), line("WOE:EN")).footer?.number)
        assertEquals("111", read(line("I1|", .91f), line("WOE-EN")).footer?.number)
        assertEquals("12A", read(line("O12A", .91f), line("WOE EN")).footer?.number)
        assertNull(read(line("OIL", .91f), line("WOE EN")).footer?.number)
        assertNull(read(line("words 057", .91f), line("WOE EN")).footer?.number)
    }
    @Test fun legacyCopyrightAndFractionArePartialNotAnInventedSet() {
        val result = read(line("©2013 Wizards of the Coast 201/229", .94f))
        assertEquals(ScanReadState.PARTIAL, result.state)
        assertEquals(listOf(LegacyFooter("201", "229", "2013")), result.legacyCandidates)
        assertNull(result.footer)
        assertFalse(result.corroboratedTuple)
        assertEquals(ScanReadState.PARTIAL, read(line("201/229", .94f)).state)
        assertEquals(ScanReadState.PARTIAL, read(line("©2013 1/1", .94f)).state)
    }
    @Test fun dotAndConcatenationWorkForAnyKnownEdition() {
        assertEquals("M15", read(line("M15.EN")).footer?.setCode)
        assertEquals("WOE", read(line("WOEFR")).footer?.setCode)
        assertEquals("fr", read(line("WOEFR")).footer?.language)
        assertEquals("SPG", read(line("SPGSP")).footer?.setCode)
        assertEquals(ScanReadState.UNREADABLE, read(line("XYZEN")).state)
    }
}
