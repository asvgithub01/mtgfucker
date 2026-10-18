package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.FastRegion

/** Shared Fast/Triple scheduler: FULL lane always runs, alternate only the rescue crop. */
internal class EdScanFastAutoPolicy {
    var region=FastRegion.ART
        private set
    private var failures=0
    private var attempts=0
    fun reset() { region=FastRegion.ART;failures=0;attempts=0 }
    fun observe(present:Boolean,reading:EdScanFastPolicy.Reading):Boolean {
        val before=region
        if(!present) reset()
        else {
            if(reading.confirmed) { failures=0;attempts=0 }
            else if(reading.reason!="stale" && reading.reason!="out_of_order") {
                attempts++
                failures=if(reading.reason=="readings") 0 else failures+1
                if(failures>=2 || attempts>=4) {
                    region=when(region) {
                        FastRegion.ART -> FastRegion.UPPER
                        FastRegion.UPPER, FastRegion.FULL -> FastRegion.ART
                    }
                    failures=0;attempts=0
                }
            }
        }
        return region!=before
    }
}
