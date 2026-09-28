package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class EdScanSavePolicyTest {
 @Test fun onlyFoilOverridesUncheckedButNotAmbiguousFinishes() {
  assertEquals("foil", EdScanSavePolicy.finish("nonfoil", setOf("foil")))
  assertEquals("nonfoil", EdScanSavePolicy.finish("nonfoil", setOf("nonfoil", "foil")))
  assertNull(EdScanSavePolicy.finish("foil", setOf("nonfoil")))
  assertNull(EdScanSavePolicy.finish("nonfoil", setOf("foil", "etched")))
 }
 @Test fun failuresRemainDistinct() {
  assertEquals(3, EdScanSavePolicy.errorCase("id_back", emptySet(), "nonfoil"))
  assertEquals(1, EdScanSavePolicy.errorCase("id", emptySet(), "nonfoil"))
  assertEquals(2, EdScanSavePolicy.errorCase("id", setOf("nonfoil"), "foil"))
 }
}
