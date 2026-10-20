package io.asv.mtgocr.ocrreader

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import org.junit.Assert.*
import org.junit.Test

class TripleManualFinishTest {
    @Test fun foilOnlyCandidateRejectsNormalBeforeAnyWrite() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val session=TripleCollectionSession(instrumentation.targetContext) {}
            val foil=CardEditionOption("fixture","Test","Test","TST","Test","1","","common",
                "foil",true,null,"","",null,null,null,null)
            val field=TripleCollectionSession::class.java.getDeclaredField("options").apply { isAccessible=true }
            @Suppress("UNCHECKED_CAST")
            val cache=field.get(session) as MutableMap<String,List<CardEditionOption>>
            cache["fixture"]=listOf(foil)
            val target=TripleScanActivity.Target("fixture","fixture","Test","TST","Test","","foil","EdScan",true)
            var called=false
            session.submit(TripleCollectionSession.State(1,target,null,"nonfoil",1,null,false,"fixture")) { card,option,error ->
                called=true
                assertNull(card);assertNull(option)
                assertTrue(error is TripleCollectionSession.FinishUnavailable)
            }
            assertTrue(called) // synchronous rejection, no writer queued
            val saved=TripleCollectionSession::class.java.getDeclaredField("saved").apply { isAccessible=true }.get(session) as Map<*,*>
            assertTrue(saved.isEmpty())
        }
    }
}
