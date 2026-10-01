package io.asv.mtgocr.ocrreader

/** Empty collections already synced are deliberate local edits, not missing backups. */
internal object CloudRestorePolicy {
    fun restoreEmpty(localEmpty: Boolean, remoteHasCards: Boolean, hasSyncedBefore: Boolean): Boolean =
        localEmpty && remoteHasCards && !hasSyncedBefore
}
