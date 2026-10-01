package io.asv.mtgocr.ocrreader

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Persisted evidence belongs to a capture, not to the editable collection printing. */
object ReviewEvidenceCache {
    fun unchangedOutcome(status: String, reason: String, evidenceAt: Long, outcomeAt: Long,
                         planned: String, current: String): Boolean =
        status=="skipped" && reason in setOf("weak","multiple","identity","language","edition_language") &&
            evidenceAt<=outcomeAt && planned.isNotBlank() && planned==current
    fun digest(files: List<File>): String = MessageDigest.getInstance("SHA-256").let { hash ->
        files.forEach { file ->
            hash.update(file.name.toByteArray())
            file.inputStream().use { input ->
                val buffer=ByteArray(8192)
                while (true) { val n=input.read(buffer); if(n<0) break; hash.update(buffer,0,n) }
            }
        }
        hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun attempt(dir: File, key: String, languageOnly: Boolean = false): String? {
        val attempt=ScanReviewStore.json(File(dir,"$key.visual-request.json"))
            ?.takeIf { it.optBoolean("both") }?.optString("attempt") ?: return null
        if(!attempt.matches(Regex("[a-f0-9-]{36}"))) return null
        val inputs=listOf(File(dir,"$key.jpg"),File(dir,"$key.json"))
        val outputs=(if(languageOnly) emptyList() else listOf(File(dir,"$key.$attempt.verification.json"))) + File(dir,"$key.$attempt.ocr.json")
        if((inputs+outputs).any { !it.isFile } || outputs.any { ScanReviewStore.json(it)==null }) return null
        val signature=digest(inputs+outputs)
        val marker=File(dir,if(languageOnly) "$key.ocr-cache.json" else "$key.evidence-cache.json")
        val cached=ScanReviewStore.json(marker)
        if(cached?.optString("attempt")==attempt) return attempt.takeIf { cached.optString("digest")==signature }
        // Migration for existing completed captures: never adopt evidence older than its inputs.
        val checked=if(languageOnly) outputs.last().lastModified() else ScanReviewStore.json(outputs.first())?.optLong("checkedAt") ?: 0L
        if(checked<=0L || inputs.any { it.lastModified()>checked } || outputs.last().length()==0L) return null
        ScanReviewStore.write(marker,JSONObject().put("attempt",attempt).put("digest",signature))
        return attempt
    }
    fun unchanged(dir: File, entry: ScanReviewStore.Entry, attempt: String, journals: List<File>): Boolean {
        val evidence=(if(entry.source=="ocr") listOf("verification","ocr","title") else listOf("ocr","title")).map { File(dir,"${entry.photoKey}.$attempt.$it.json") }
        if(evidence.any { !it.isFile }) return false
        val title=ScanReviewStore.json(evidence.last()) ?: return false
        if(title.optString("printingUuid")!=entry.card.printingUuid.orEmpty()) return false
        val fingerprint=ReliableReviewJournal.fingerprint(entry.card)
        for(journal in journals) {
            val outcome=ScanReviewStore.json(File(journal,"${entry.photoKey}.result.json")) ?: continue
            if(outcome.optInt("rulesVersion")!=ReliableReviewPolicy.VERSION) return false
            val plan=ScanReviewStore.json(File(journal,"${entry.photoKey}.plan.json")) ?: return false
            return unchangedOutcome(outcome.optString("status"),outcome.optString("reason"),
                evidence.maxOf { it.lastModified() },outcome.optLong("at"),plan.optString("fingerprint"),fingerprint)
        }
        return false
    }
}
