package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class TripleEncounterGateTest {
 @Test fun updatesStayInSameEventButReturningCopyIsNew() {
  val gate=TripleEncounterGate()
  val first=gate.observe("a")
  repeat(20) { assertEquals(first,gate.observe("a")) }
  gate.missing(100);gate.missing(800);assertEquals(first,gate.observe("a"))
  gate.missing(1000);gate.missing(2001);assertEquals(first+1,gate.observe("a"))
  assertEquals(first+2,gate.observe("b"));assertEquals(first+3,gate.observe("a"))
 }
 @Test fun editionEnrichmentKeepsIdentityEventButDoesNotCancelObservedAbsence() {
  val g=TripleEncounterGate();val event=g.observe("ocr-name:medusa")
  g.rebindIdentity("printing-uuid");assertEquals(event,g.observe("printing-uuid"))
  g.missing(0);g.missing(1001);g.rebindIdentity("printing-uuid")
  assertEquals(event+1,g.observe("printing-uuid"))
 }
 @Test fun positivePresenceInterruptsMissingInterval() {
  val g=TripleEncounterGate();val first=g.observe("a")
  g.missing(100);g.present();g.missing(1200)
  assertEquals(first,g.observe("a"))
 }
}
