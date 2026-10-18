package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class EdScanFastIdentityTest {
    private fun c(id:String,s:Float,name:String?=id,set:String="",date:String="",price:Double?=null)=
        EdScanFastIdentity.Candidate(id,s,name,set,date,price)
    private fun confirm(a:List<EdScanFastIdentity.Candidate>,b:List<EdScanFastIdentity.Candidate> = a):EdScanFastPolicy.Reading {
        val p=EdScanFastPolicy()
        fun step(x:List<EdScanFastIdentity.Candidate>,t:Long):EdScanFastPolicy.Reading {
            val e=EdScanFastIdentity.resolve(x)
            return p.observe(true,e.identity,e.score,e.runner,t,t+900)
        }
        assertFalse(step(a,1000).confirmed)
        return step(b,2100)
    }
    @Test fun sonyBrontodonPhotoIgnoresListPrintingNotDifferentCards() {
        val a=listOf(c("xln",.7141468f,"brontodon","xln","2017-09-29"),
            c("list",.7107666f,"brontodon","plst","2020-09-25"),c("insight",.486527f))
        assertTrue(confirm(a,a.map { if(it.id=="list") it.copy(score=.715f) else it }).confirmed)
        assertEquals("xln",EdScanFastIdentity.resolve(a).printing)
        assertEquals(listOf("xln","list"),EdScanFastIdentity.resolve(a).alternatives)
    }
    @Test fun sonyWitchesTraceConfirmsAcrossPrintingChanges() {
        val a=listOf(c("chr",.95389104f,"witches"),c("other",.9107851f,"witches"),c("different",.7001842f))
        assertTrue(confirm(a,listOf(a[1].copy(score=.96f),a[0],a[2])).confirmed)
    }
    @Test fun sonyLowScorePhotosStillRejected() {
        for(a in listOf(
            listOf(c("shade-fbb",.54649854f,"shade"),c("shade30a",.5371579f,"shade"),c("other",.46853474f)),
            listOf(c("clue",.45654124f),c("otter",.42871645f),c("mummy",.4011218f)),
            listOf(c("paer",.6467499f,"kiran"),c("aer",.64320576f,"kiran"),c("comet",.46102172f))
        )) assertEquals("score",confirm(a).reason)
    }
    @Test fun distinctIdentityTieAndUnknownRunnerCannotBeIgnoredEvenIfList() {
        assertEquals("margin",confirm(listOf(c("a",.90f),c("b",.89f,"b","plst"))).reason)
        assertEquals("margin",confirm(listOf(c("a",.90f),c("unknown",.89f,null))).reason)
        assertFalse(confirm(listOf(c("unknown",.9f,null),c("b",.5f))).confirmed)
        assertEquals("no_runner",confirm(listOf(c("a",.9f,"same"),c("b",.89f,"same"))).reason)
    }
    @Test fun printingOrderIsNonListThenNewestThenCheapestKnownPrice() {
        val a=listOf(c("list",.95f,"a","plst","2026-01-01",.01),
            c("old",.94f,"a","old","2020-01-01",.01),c("expensive",.94f,"a","new","2025-01-01",5.0),
            c("cheap",.94f,"a","new","2025-01-01",1.0),c("unknown",.94f,"a","new","2025-01-01"),
            c("other",.6f))
        assertEquals(listOf("cheap","expensive","unknown","old","list"),EdScanFastIdentity.resolve(a).alternatives)
        assertEquals("list",EdScanFastIdentity.resolve(listOf(a[0],a.last())).printing)
    }
    @Test fun preferenceCannotPromoteWeakUnrelatedOrDistantPrinting() {
        val a=listOf(c("strong",.9f,"a","plst"),c("weak",.7f,"a","new","2026"),c("other",.6f))
        assertEquals("strong",EdScanFastIdentity.resolve(a).printing)
        assertTrue(confirm(a).confirmed)
        assertFalse(confirm(a,listOf(c("changed",.9f),c("other",.6f))).confirmed)
    }
    @Test fun northernPaladinPrefersOrdinaryRecentSetNotCollectorReplica() {
        val cards=listOf(c("30a",.95f,"northern paladin","30a","2022-11-28",150.0),
            c("lea",.94f,"northern paladin","lea","1993-08-05",200.0),
            c("4ed",.94f,"northern paladin","4ed","1995-04-01",.4),
            c("new",.94f,"northern paladin","dmu","2022-09-09",1.0),c("other",.60f))
        val e=EdScanFastIdentity.resolve(cards)
        assertEquals("new",e.printing)
        assertEquals(.95f,e.score!!,0f);assertEquals(.60f,e.runner!!,0f)
        assertEquals("new",EdScanFastIdentity.resolve(cards.reversed()).printing)
        for(set in listOf("CED","cei","sum","lea","leb","30a"))
            assertEquals("ordinary",EdScanFastIdentity.resolve(listOf(c("special",.95f,"same",set,"2030"),
                c("ordinary",.94f,"same","4ed","1995"),c("other",.6f))).printing)
        // Same-card but weak/other-art results cannot be promoted by preference.
        assertEquals("30a",EdScanFastIdentity.resolve(listOf(cards[0],cards[3].copy(score=.7f),cards.last())).printing)
    }

    @Test fun invalidScoresCannotRemoveACompetitorAndManufactureMargin() {
        assertNull(EdScanFastIdentity.resolve(listOf(c("a",.9f),c("b",Float.NaN),c("c",.6f))).identity)
        assertNull(EdScanFastIdentity.resolve(emptyList()).identity)
    }
    @Test fun unknownInvalidPricesDoNotMeanFreeAndOrderIsDeterministic() {
        val a=listOf(c("unknown",.9f,"a","x","2025",Double.NaN),
            c("paid",.9f,"a","x","2025",2.0),c("negative",.9f,"a","x","2025",-1.0),c("b",.6f))
        assertEquals("paid",EdScanFastIdentity.resolve(a).printing)
        assertEquals(EdScanFastIdentity.resolve(a),EdScanFastIdentity.resolve(a.reversed()))
    }
}
