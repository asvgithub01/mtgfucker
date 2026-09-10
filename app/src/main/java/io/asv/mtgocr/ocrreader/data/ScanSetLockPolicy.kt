package io.asv.mtgocr.ocrreader.data

import java.util.Locale

/** Expands a base set lock to official companion subsets that are scanned with that set. */
internal object ScanSetLockPolicy {
    private val companionSets = mapOf(
        "SOS" to setOf("SOA"), // Secrets of Strixhaven -> Mystical Archive
        "STX" to setOf("STA")  // Strixhaven: School of Mages -> Mystical Archive
    )

    fun expand(lockedSetCodes: Set<String>): Set<String> = buildSet {
        for (rawCode in lockedSetCodes) {
            val code = rawCode.trim().uppercase(Locale.US)
            if (code.isEmpty()) continue
            add(code)
            addAll(companionSets[code].orEmpty())
        }
    }
}
