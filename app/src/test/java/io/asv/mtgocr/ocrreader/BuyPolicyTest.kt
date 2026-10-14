package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class BuyPolicyTest {
    private fun e(id:String="a",finish:String="nonfoil",q:Int=1)=BuyEntry(id,"uuid","Card","FRA","3",finish,"","123",q)
    @Test fun addsQuantityWithoutDuplicatingPrinting() { val result=BuyPolicy.add(listOf(e()),listOf(e("b",q=2)));assertEquals(1,result.size);assertEquals("a",result.single().id);assertEquals(3,result.single().quantity) }
    @Test fun finishAndLanguageAreDistinct() { assertEquals(3,BuyPolicy.add(listOf(e()),listOf(e("b","foil"),e("c").copy(language="es"))).size) }
    @Test fun stableCompositionIgnoresOrderButNotQuantity() { assertEquals(BuyPolicy.composition(listOf(e(),e("b"))),BuyPolicy.composition(listOf(e("b"),e())));assertNotEquals(BuyPolicy.composition(listOf(e())),BuyPolicy.composition(listOf(e(q=2)))) }
    @Test fun missingNeverBecomesZeroPricedCoverage() { val s=BuySnapshot(1,"x",listOf(BuyQuote("a",4.0,"d"),BuyQuote("b",null,"")));assertEquals(4.0,s.total,0.0);assertEquals(1,s.missing);assertFalse(BuyPolicy.comparable(s,s)) }
    @Test fun comparisonRejectsMembershipChanges() { val s=BuySnapshot(1,"a",listOf(BuyQuote("a",4.0,"d")));assertTrue(BuyPolicy.comparable(s,s.copy(at=2)));assertFalse(BuyPolicy.comparable(s,s.copy(composition="b"))) }
    @Test fun wantsTextUsesMax150EntriesAndExpansion() { val batches=BuyPolicy.wantsBatches((1..301).map { e(it.toString()).copy(setName="Final Fantasy") });assertEquals(listOf(150,150,1),batches.map { it.lines().size });assertEquals("1 Card (Final Fantasy)",batches.first().lines().first()) }
    @Test fun deletedEntriesDoNotReappearAfterMerge() { assertEquals(listOf(e("b")),BuyPolicy.add(emptyList(),listOf(e("b")))) }
}
