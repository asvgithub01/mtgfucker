package io.asv.mtgocr.ocrreader.ui.camera

import io.asv.mtgocr.ocrreader.OcrTitleRegion
import org.junit.Assert.*
import org.junit.Test

class OcrLumaExperimentDeviceTest {
    @Test fun experimentalPathPreservesRulesMaskAndChromaForEveryRotation() {
        val w = 320; val h = 240
        for (rotation in 0..3) {
            val source = ByteArray(w*h*3/2) { ((it * 17) % 256).toByte() }
            val legacy = source.copyOf(); val enhanced = source.copyOf()
            OcrLumaEnhancer.enhance(legacy,w,h,rotation)
            OcrLumaEnhancer.enhanceExperimental(enhanced,w,h,rotation)
            assertFalse(legacy.contentEquals(enhanced))
            val title = OcrTitleRegion.forFrame(if(rotation%2==1) h else w, if(rotation%2==1) w else h)
            for (i in source.indices) {
                val x=i%w;val y=i/w
                val ux=when(rotation){1->h-1-y;2->w-1-x;else->if(rotation==3)y else x}
                val uy=when(rotation){1->x;2->h-1-y;else->if(rotation==3)w-1-x else y}
                if (i>=w*h || !title.contains(ux,uy)) assertEquals("rotation=$rotation pixel=$i",legacy[i],enhanced[i])
            }
        }
    }
}
