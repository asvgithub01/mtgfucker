package io.asv.collectorvision

import org.junit.Assert.*
import org.junit.Test

class IdentitySearchTest {
    @Test fun boundedRankingMatchesFullSortIncludingTies() {
        val ids=(0 until 2000).map { "p$it" }
        val keys=ids.withIndex().associate { it.value to "group${it.index%151}" }
        val random=java.util.Random(42)
        repeat(20) {
            val scores=FloatArray(ids.size) { random.nextInt(100)/100f }
            val winner=scores.indices.maxBy { scores[it] }
            val groups=ids.indices.groupBy { keys.getValue(ids[it]) }
            val winningKey=keys.getValue(ids[winner])
            val printings=groups.getValue(winningKey).sortedByDescending { scores[it] }.take(8)
            val rivals=groups.filterKeys { it!=winningKey }.values.map { rows -> rows.maxBy { scores[it] } }
                .sortedByDescending { scores[it] }.take(8)
            val expected=(printings+rivals).sortedByDescending { scores[it] }.map { Hit(ids[it],scores[it]) }
            assertEquals(expected,IdentitySearch(ids,keys).select(scores))
        }
    }
    @Test fun moreThanEightPrintingsCannotHideActualRival() {
        val ids=(0..100).map { "p$it" }+"rival"
        val search=IdentitySearch(ids,ids.associateWith { if(it=="rival") "other" else "lord" })
        val scores=FloatArray(ids.size) { if(it==101) .60f else .85f-it*.001f }
        val hits=search.select(scores)
        assertEquals(9,hits.size);assertEquals("rival",hits.last().cardId);assertTrue(search.strong(hits))
        scores[101]=.84f;assertFalse(search.strong(search.select(scores)))
    }
    @Test fun oppositeOrientationRivalSurvivesMerge() {
        val keys=(0..20).associate { "p$it" to "same" }+mapOf("r" to "other")
        val s=IdentitySearch(keys.keys.toList(),keys)
        val merged=s.merge((0..20).map { Hit("p$it",.9f-it*.001f) }+Hit("r",.89f))
        assertTrue(merged.any { it.cardId=="r" });assertFalse(s.strong(merged))
    }
    @Test fun unknownsCompeteAndMissingRivalIsNeverZero() {
        val s=IdentitySearch(listOf("a","b"),mapOf("a" to "card"))
        assertFalse(s.strong(s.select(floatArrayOf(.9f,.89f))))
        assertFalse(s.strong(s.select(floatArrayOf(.9f,Float.NEGATIVE_INFINITY))))
        assertTrue(s.select(floatArrayOf(Float.NaN,Float.NEGATIVE_INFINITY)).isEmpty())
    }
}
