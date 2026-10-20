package io.asv.mtgocr.ocrreader

/** A model reference identifies a face; the catalog identifies the complete printing. */
internal data class TripleVisualReference private constructor(val visualId:String, val catalogId:String, val back:Boolean) {
    fun localImage(front:String):String {
        if (!back) return front
        // Only rewrite the known Scryfall face path, never an arbitrary image provider.
        val uri=runCatching { java.net.URI(front) }.getOrNull() ?: return ""
        return if(uri.host=="cards.scryfall.io" && uri.path.contains("/front/"))
            front.replace("/front/","/back/") else ""
    }
    companion object {
        private val pattern=Regex("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(_back)?")
        fun parse(id:String):TripleVisualReference? {
            val match=pattern.matchEntire(id) ?: return null
            return TripleVisualReference(id,match.groupValues[1].lowercase(java.util.Locale.ROOT),match.groupValues[2].isNotEmpty())
        }
    }
}
