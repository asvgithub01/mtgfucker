package io.asv.mtgocr.ocrreader

import org.junit.Assert.*
import org.junit.Test

class TripleVisualReferenceTest {
    private val id="4f7a6892-5f6b-49aa-9b4f-3fcf20236306"
    @Test fun bothFacesResolveExactShowcasePrinting() {
        val front=TripleVisualReference.parse(id)!!
        val back=TripleVisualReference.parse(id+"_back")!!
        assertEquals(front.catalogId,back.catalogId)
        assertFalse(front.back);assertTrue(back.back)
        assertEquals(id+"_back",back.visualId)
        assertNotEquals(TripleVisualReference.parse("c13e8e3d-2a6b-4782-a3c9-71af7336a881")!!.catalogId,back.catalogId)
    }
    @Test fun rejectsMalformedOrRepeatedSuffixes() {
        listOf("", "-".repeat(36),id+"_back_back",id+"_front",id+"/back", " $id", "ocr-name:Test").forEach {
            assertNull(it,TripleVisualReference.parse(it))
        }
        assertEquals(id,TripleVisualReference.parse(id.uppercase())!!.catalogId)
    }
    @Test fun faceImageDoesNotLeakFrontOrRewriteUnknownProviders() {
        val url="https://cards.scryfall.io/normal/front/4/f/$id.jpg?123"
        assertEquals(url,TripleVisualReference.parse(id)!!.localImage(url))
        assertEquals(url.replace("/front/","/back/"),TripleVisualReference.parse(id+"_back")!!.localImage(url))
        assertEquals("",TripleVisualReference.parse(id+"_back")!!.localImage("https://other.example/front/test.jpg"))
    }
}
