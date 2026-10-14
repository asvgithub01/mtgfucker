package io.asv.mtgocr.ocrreader

/** Reserve full-height card art first; controls use the remaining width and scroll if necessary. */
internal object TripleGalleryPopupLayout {
    data class Size(val cardWidth:Int,val menuWidth:Int,val height:Int)
    fun fit(width:Int,availableHeight:Int,desiredHeight:Int,minMenuWidth:Int,gap:Int):Size {
        val ratio=63f/88f
        val height=minOf(desiredHeight,availableHeight,((width-minMenuWidth-gap)/ratio).toInt()).coerceAtLeast(1)
        val cardWidth=(height*ratio).toInt().coerceAtLeast(1)
        return Size(cardWidth,width-cardWidth-gap,height)
    }
}
