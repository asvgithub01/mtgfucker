package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleLanguageEvidenceTest {
 @Test fun nativeScriptRequiresMultipleLetters() {
  assertEquals("ja",TripleLanguageEvidence.scriptLanguage("クリーチャーを対象とする"))
  assertEquals("ko",TripleLanguageEvidence.scriptLanguage("생물 하나를 목표로 정한다"))
  for(t in listOf("你控制的生物", "Add one mana", "Adicione uma mana", "クリ", ""))
   assertNull(TripleLanguageEvidence.scriptLanguage(t))
 }
 @Test fun confirmationSurvivesUnreadableFramesButDoesNotLeakToNewIdentity() {
  val c=TripleLanguageConsensus()
  assertNull(c.observe("a","pt"));assertNull(c.observe("a",null));assertEquals("pt",c.observe("a","pt"))
  repeat(5) { assertEquals("pt",c.observe("a",null)) }
  assertEquals("pt",c.observe("a","en"));assertEquals("pt",c.observe("a","pt"))
  repeat(2) { assertEquals("pt",c.observe("a","es")) };assertEquals("es",c.observe("a","es"))
  assertNull(c.observe("b","es"));c.reset();assertNull(c.observe("b","es"))
 }
 private fun title(vararg languages:String)=TitleLanguageIndex.Evidence(languages.map { TitleLanguageIndex.Match("Elfos de Llanowar","Llanowar Elves","Elfos de Llanowar",it) })
 @Test fun sharedSpanishPortugueseTitleNeedsRulesAndConflictsAreNotOverwritten() {
  assertNull(TripleLanguageFusion.decide("",null,title("es","pt")).code)
  assertEquals("pt",TripleLanguageFusion.decide("Adicione",null,title("es","pt")).code)
  assertEquals("es",TripleLanguageFusion.decide("Agrega",null,title("es","pt")).code)
  assertEquals("pt",TripleLanguageFusion.decide("",null,title("pt")).code)
  assertTrue(TripleLanguageFusion.decide("Add",null,title("pt")).conflict)
 }
 @Test fun chineseStatisticsAndAmbiguousItalianVerbDoNotManufactureLanguage() {
  assertNull(TripleLanguageFusion.decide("", "zhs", title()).code)
  assertNull(TripleLanguageFusion.decide("sacrifica",null,title()).code)
 }
 @Test fun latinReadingsTieAndSparseScriptDoesNotWin() {
  assertEquals(0,TripleLanguageEvidence.scriptScore("クリ Add one mana"))
  assertTrue(TripleLanguageEvidence.scriptScore("クリーチャー")>0)
  assertEquals(0,TripleLanguageEvidence.scriptScore("クリーチャ Add one mana of any color to your mana pool and draw a card then discard a card"))
 }
}
