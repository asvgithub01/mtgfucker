package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class HistoricalFooterEvidenceTest {
    private fun line(text: String, top: Float = .94f, pass: Int = 0) =
        PrintingOcrLine(pass, text, .1f, top, .8f, top + .015f)
    @Test fun yearAndArtistSurviveMissingCollectorNumber() {
        val read = StructuredPrintingEvidence.read(listOf(line("Ilus. Robert Bliss", .91f),
            line("C1996 Wizards of the Coast, Inc.")), emptySet())
        assertEquals(ScanReadState.PARTIAL, read.state)
        assertEquals(listOf("1996"), read.historical.years.values)
        assertEquals(listOf("Robert Bliss"), read.historical.artists.values)
        assertEquals(ScanReadState.UNREADABLE, read.historical.fractions.state)
        assertNull(read.footer)
    }
    @Test fun repairsYearWithoutChangingArtistOrInventingFraction() {
        val read = HistoricalFooterEvidence.read(listOf(line("Cliff Childs", .91f), line("& 20I3Wuards ol the 2100")))
        assertEquals(listOf("2013"), read.years.values)
        assertEquals(listOf("Cliff Childs"), read.artists.values)
        assertTrue(read.fractions.values.isEmpty())
        assertEquals("& 20I3Wuards ol the 2100", read.years.observations.single().source.text)
    }
    @Test fun labelledArtistSurvivesMissingCopyright() {
        val read = HistoricalFooterEvidence.read(listOf(line("Ilusc. Robert Bliss", .91f)))
        assertEquals(listOf("Robert Bliss"), read.artists.values)
        assertTrue(read.years.values.isEmpty())
        assertTrue(HistoricalFooterEvidence.read(listOf(line("Flying first strike", .91f))).artists.values.isEmpty())
    }
    @Test fun fractionsAreIndependentAndScopedRepairsRequireDigits() {
        assertEquals(listOf("201/229"), HistoricalFooterEvidence.read(listOf(line("2OI/229"))).fractions.values)
        assertTrue(HistoricalFooterEvidence.read(listOf(line("III/OOO"), line("999/120"))).fractions.values.isEmpty())
    }
    @Test fun copyrightRangeIsOneObservationNotConflict() {
        assertEquals(listOf("1993–2003"), HistoricalFooterEvidence.read(listOf(line("© 1993–2003 Wizards"))).years.values)
    }
    @Test fun contradictoryPassesRemainConflictWithoutInventingSet() {
        val read = StructuredPrintingEvidence.read(listOf(line("©2013 Wizards"), line("©2018 Wizards", pass=1)), emptySet())
        assertEquals(ScanReadState.CONFLICT, read.historical.years.state)
        assertEquals(ScanReadState.PARTIAL, read.state)
        assertTrue(read.candidates.isEmpty())
    }
    @Test fun invalidGeometryRulesAndCrossPassArtistAreIgnored() {
        val read = HistoricalFooterEvidence.read(listOf(line("©2013 Wizards", .7f), line("©2013 Wizards").copy(right=Float.NaN),
            line("Robert Bliss", .91f, 1), line("©1996 Wizards", pass=0)))
        assertEquals(listOf("1996"), read.years.values)
        assertTrue(read.artists.values.isEmpty())
    }
    @Test fun modernFooterStillWinsWithoutHistoricalEditionInference() {
        val read = StructuredPrintingEvidence.read(listOf(line("ORI EN", .88f), line("©2015 Wizards")), setOf("ORI"))
        assertEquals(ScanReadState.READ, read.state)
        assertEquals("ORI", read.footer?.setCode)
    }
    @Test fun terminalYearAndFullRangeAreCompatibleWithoutLosingProvenance() {
        val read = HistoricalFooterEvidence.read(listOf(line("©1993-2007 Wizards"), line("©2007 Wizards", pass=3)))
        assertEquals(ScanReadState.READ, read.years.state)
        assertEquals(listOf("1993–2007", "2007"), read.years.values)
        assertEquals(2, read.years.observations.size)
    }
    @Test fun differentRangesAndNonTerminalYearsStillConflict() {
        for (other in listOf("1993", "2006", "1994-2007", "1993-2006")) {
            val read = HistoricalFooterEvidence.read(listOf(line("©1993-2007 Wizards"), line("©$other Wizards", pass=3)))
            assertEquals(other, ScanReadState.CONFLICT, read.years.state)
        }
    }
    @Test fun rightArtistUsesAdjacentSamePassCopyright() {
        val artist = line("David Day", .93f).copy(left=.75f, right=.91f)
        assertEquals(listOf("David Day"), HistoricalFooterEvidence.read(listOf(artist,
            line("©1993-2007 Wizards 60/180", .96f))).artists.values)
        assertTrue(HistoricalFooterEvidence.read(listOf(artist,
            line("©2007 Wizards", .96f, 1))).artists.values.isEmpty())
        assertTrue(HistoricalFooterEvidence.read(listOf(artist)).artists.values.isEmpty())
    }
    @Test fun actualCopyrightGlyphIsRemovedButLetterNamesAreNotRewritten() {
        assertEquals(listOf("Douglas Shuler"), HistoricalFooterEvidence.read(listOf(line("Illus. ©Douglas Shuler"))).artists.values)
        assertEquals(listOf("Omar Rayyan"), HistoricalFooterEvidence.read(listOf(line("Illus. Omar Rayyan"))).artists.values)
        assertEquals(listOf("ODougas Shuler"), HistoricalFooterEvidence.read(listOf(line("Illus. ODougas Shuler"))).artists.values)
    }
    @Test fun collectorContradictionRemainsVisible() {
        assertEquals(ScanReadState.CONFLICT, HistoricalFooterEvidence.read(listOf(line("17/301"),
            line("17/501", pass=3))).fractions.state)
    }
    @Test fun denominatorConflictDoesNotEraseAgreementOnNumber() {
        val a = line("©2006 Wizards 17/301", pass = 0)
        val b = line("©2006 Wizards 17/30", pass = 3)
        val read = HistoricalFooterEvidence.read(listOf(a, b))
        assertEquals(ScanReadState.CONFLICT, read.fractions.state)
        assertEquals(listOf("17"), read.collectorNumbers.values)
        assertEquals(ScanReadState.READ, read.collectorNumbers.state)
        assertEquals(listOf("301", "30"), read.printedTotals.values)
        assertEquals(ScanReadState.CONFLICT, read.printedTotals.state)
        assertEquals(listOf(a, b), read.collectorNumbers.observations.map { it.source })
        assertEquals(listOf("17/301", "17/30"), read.fractions.values)
    }
    @Test fun numberConflictDoesNotEraseAgreementOnTotal() {
        val read = HistoricalFooterEvidence.read(listOf(line("17/301"), line("77/301", pass = 3)))
        assertEquals(ScanReadState.CONFLICT, read.collectorNumbers.state)
        assertEquals(ScanReadState.READ, read.printedTotals.state)
        assertEquals(listOf("301"), read.printedTotals.values)
    }
    @Test fun bothComponentsRemainConflictingWithoutMajorityVote() {
        val read = HistoricalFooterEvidence.read(listOf(line("17/301"), line("77/300", pass = 3), line("17/301", pass = 6)))
        assertEquals(ScanReadState.CONFLICT, read.collectorNumbers.state)
        assertEquals(ScanReadState.CONFLICT, read.printedTotals.state)
        assertEquals(listOf("17/301", "77/300"), read.fractions.values)
    }
    @Test fun missingOrRejectedFractionsDoNotInventPartialNumbers() {
        for (text in listOf("©2006 Wizards", "17/3", "999/120", "III/OOO")) {
            val read = HistoricalFooterEvidence.read(listOf(line(text)))
            assertEquals(text, ScanReadState.UNREADABLE, read.collectorNumbers.state)
            assertEquals(text, ScanReadState.UNREADABLE, read.printedTotals.state)
        }
    }
    @Test fun acceptedNumericRepairsAndLeadingZerosAreProjectedWithoutNewCorrections() {
        val read = HistoricalFooterEvidence.read(listOf(line("0I7/3O1")))
        assertEquals(listOf("17"), read.collectorNumbers.values)
        assertEquals(listOf("301"), read.printedTotals.values)
        assertEquals("0I7/3O1", read.printedTotals.observations.single().source.text)
    }
    @Test fun trademarkCopyrightOcrAndInitialOneConfusionRecoverRange() {
        for (text in listOf("M&C I993-2009 Wards of the Caoast LC 1162", "TM & C I993-2009", "™ & C l993–2009", "M&C1993-2009")) {
            val result = HistoricalFooterEvidence.read(listOf(line(text)))
            assertEquals(text, listOf("1993–2009"), result.years.values)
            assertEquals(text, result.years.observations.single().source.text)
            assertTrue(result.fractions.values.isEmpty())
        }
    }
    @Test fun initialOneRepairNeedsCopyrightContextAndValidYear() {
        assertEquals(listOf("1993"), HistoricalFooterEvidence.read(listOf(line("© I993"))).years.values)
        for (text in listOf("I993", "M&C IIII", "M&C Artist Name", "M&C19930", "M&C I1993", "M&C 200o9")) {
            assertTrue(text, HistoricalFooterEvidence.read(listOf(line(text))).years.values.isEmpty())
        }
    }
    @Test fun trademarkYearRemainsFooterScopedAndDoesNotRepairCollectorSlash() {
        assertTrue(HistoricalFooterEvidence.read(listOf(line("M&C I993-2009", .7f))).years.values.isEmpty())
        val result = HistoricalFooterEvidence.read(listOf(line("M&C I993-2009 1162")))
        assertTrue(result.collectorNumbers.values.isEmpty())
        assertTrue(result.printedTotals.values.isEmpty())
    }
    @Test fun truncatedCopyrightRangeIsNotAStandaloneYearOrContradiction() {
        val read = HistoricalFooterEvidence.read(listOf(line("M&C1993-2006"), line("M&C1993-20", pass = 9)))
        assertEquals(listOf("1993–2006"), read.years.values)
        assertEquals(ScanReadState.READ, read.years.state)
        for (text in listOf("M&C1993-20", "©1993-200o9", "©1993—")) {
            assertTrue(text, HistoricalFooterEvidence.read(listOf(line(text))).years.values.isEmpty())
        }
    }
}
