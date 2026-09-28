package io.asv.mtgocr.ocrreader

internal object EdScanSavePolicy {
    fun finish(requested: String, available: Set<String>): String? = when {
        requested in available -> requested
        requested == "nonfoil" && available == setOf("foil") -> "foil"
        else -> null
    }
    fun errorCase(id: String, available: Set<String>, requested: String): Int = when {
        id.endsWith("_back") -> 3
        available.isEmpty() -> 1
        finish(requested, available) == null -> 2
        else -> 0
    }
}
