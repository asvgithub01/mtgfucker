package io.asv.mtgocr.ocrreader.domain.library

/** Explicit intent, never a serialized UI projection. Zero copies must use DeleteCard. */
sealed interface LibraryMutation {
    data class EditCard(val id: String, val quantity: Int, val condition: String) : LibraryMutation
    data class DeleteCard(val id: String, val confirmed: Boolean) : LibraryMutation
    /** Legacy groups exist through memberships; an empty group cannot be persisted there. */
    data class AssignGroup(val name: String, val cardIds: Set<String>) : LibraryMutation
    data class RenameGroup(val name: String, val newName: String) : LibraryMutation
    data class DeleteGroup(val name: String, val confirmed: Boolean) : LibraryMutation
    data class RemoveFromGroup(val name: String, val cardIds: Set<String>) : LibraryMutation
}

enum class MutationFailure {
    INVALID_INPUT, MISSING_CARD, MISSING_GROUP, DUPLICATE_GROUP, CORRUPT_COLLECTION, CONFIRMATION_REQUIRED,
    STALE_REVISION, LIBRARY_CHANGED, WRITES_DISABLED, STORAGE_FAILURE
}
