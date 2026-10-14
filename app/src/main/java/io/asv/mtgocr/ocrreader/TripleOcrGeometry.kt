package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.Corner

/** Detector chooses the shortest edge, which may be the BOTTOM edge on perspective shots.
 * OCR without Milo uses the upper of the two short edges for the upright title region.
 */
internal object TripleOcrGeometry {
    fun upright(corners:List<Corner>):List<Corner> {
        if(corners.size!=4) return corners
        return if(corners[0].y+corners[1].y>corners[2].y+corners[3].y)
            corners.drop(2)+corners.take(2) else corners
    }
}
