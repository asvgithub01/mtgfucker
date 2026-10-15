package io.asv.mtgocr.ocrreader

/** Never silently overwrite concurrent edits, resurrect a tracked deletion or confuse missing with empty. */
internal object CloudBuylistPolicy {
    enum class Action { UPLOAD, RESTORE, SAME, CONFLICT }
    fun action(local: String?, remote: String?, base: String?, localBase: String? = base): Action = when {
        local==remote || (local==localBase && remote==base) -> Action.SAME
        local==null -> Action.RESTORE
        remote==null -> Action.UPLOAD
        local==localBase -> Action.RESTORE
        remote==base -> Action.UPLOAD
        else -> Action.CONFLICT
    }
}
