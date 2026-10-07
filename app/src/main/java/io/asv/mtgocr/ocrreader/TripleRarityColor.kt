package io.asv.mtgocr.ocrreader

object TripleRarityColor {
    fun color(rarity:String?):Int=when(rarity?.trim()?.lowercase(java.util.Locale.ROOT)) {
        "common" -> 0xFF37FF86.toInt()
        "uncommon" -> 0xFFD4DFE8.toInt()
        "rare" -> 0xFFFFD05A.toInt()
        "mythic", "mythic rare" -> 0xFFFF782D.toInt()
        "special", "bonus" -> 0xFFC88CFF.toInt()
        else -> 0xFF92A0AA.toInt()
    }
}
