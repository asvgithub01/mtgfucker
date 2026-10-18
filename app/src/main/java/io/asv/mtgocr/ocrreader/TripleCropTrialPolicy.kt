package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.FastRegion

/** Triple-only experiment. Full-card search first; rescue remains FULL + crop, not crop alone. */
internal class TripleCropTrialPolicy(private val delayed:Boolean=false) {
    private val rescue=EdScanFastAutoPolicy()
    private var failures=0
    private var rescuing=!delayed
    val region:FastRegion get()=if(rescuing) rescue.region else FastRegion.FULL
    fun reset() { failures=0;rescuing=!delayed;rescue.reset() }
    fun observe(present:Boolean,reading:EdScanFastPolicy.Reading):Boolean {
        val before=region
        if(!present) reset()
        else if(rescuing) rescue.observe(true,reading)
        else when(reading.reason) {
            "confirmed", "readings" -> failures=0
            "score", "margin", "no_candidate", "no_runner" -> {
                failures++
                if(failures>=2) rescuing=true
            }
        }
        return region!=before
    }
}
