package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.library.LibraryMutation
import io.asv.mtgocr.ocrreader.domain.library.MutationFailure
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardCondition
import io.asv.mtgocr.ocrreader.model.CardInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

internal sealed interface PreparedLibraryMutation {
    data class Ready(val collection: Biblio, val deletedIds: Set<String>) : PreparedLibraryMutation
    data class Rejected(val reason: MutationFailure) : PreparedLibraryMutation
}

/**
 * Pure preparation on the complete detached legacy graph, not CollectionRow.
 * No disk, Room, scanner globals, photo cleanup or implicit group-to-deck migration.
 * Publication requires a coordinated writer; preparing a mutation does not save it.
 */
internal object LegacyLibraryMutation {
    fun prepare(source: Biblio, command: LibraryMutation): PreparedLibraryMutation {
        fun reject(reason: MutationFailure) = PreparedLibraryMutation.Rejected(reason)
        if (source.cards == null || source.cards.any { it == null }) return reject(MutationFailure.CORRUPT_COLLECTION)
        // snapshotForPersistence normalizes historical null lists. A command must preserve
        // even those untouched fields; detach through the existing Serializable contract.
        val copy = detach(source)
        val byId = linkedMapOf<String, CardInfo>()
        for (card in copy.cards) {
            if (card.ensureCollectionItemId() || byId.put(card.collectionItemId, card) != null)
                return reject(MutationFailure.CORRUPT_COLLECTION)
        }
        fun validName(name: String) = name.isNotBlank() && name == name.trim() &&
            name.length <= 60 && name.none { Character.isISOControl(it) }
        // Legacy getters initialize null lists. Query a separate copy, then touch only
        // rows whose memberships really change in the graph prepared for publication.
        val memberships by lazy { byId.mapValues { it.value.snapshotForPersistence().groups.toList() } }
        fun groupExists(name: String) = memberships.values.any { name in it }
        var deleted = emptySet<String>()
        when (command) {
            is LibraryMutation.EditCard -> {
                if (command.quantity <= 0 || command.condition !in CardCondition.codes())
                    return reject(MutationFailure.INVALID_INPUT)
                val card = byId[command.id] ?: return reject(MutationFailure.MISSING_CARD)
                card.quantityCount = command.quantity
                card.condition = command.condition
            }
            is LibraryMutation.DeleteCard -> {
                if (!command.confirmed) return reject(MutationFailure.CONFIRMATION_REQUIRED)
                val card = byId[command.id] ?: return reject(MutationFailure.MISSING_CARD)
                copy.cards.remove(card)
                deleted = setOf(command.id)
            }
            is LibraryMutation.AssignGroup -> {
                if (!validName(command.name) || command.cardIds.isEmpty()) return reject(MutationFailure.INVALID_INPUT)
                if (command.cardIds.any { it !in byId }) return reject(MutationFailure.MISSING_CARD)
                command.cardIds.forEach { byId.getValue(it).addGroup(command.name) }
            }
            is LibraryMutation.RenameGroup -> {
                if (!validName(command.newName)) return reject(MutationFailure.INVALID_INPUT)
                if (!groupExists(command.name)) return reject(MutationFailure.MISSING_GROUP)
                if (command.name != command.newName && groupExists(command.newName))
                    return reject(MutationFailure.DUPLICATE_GROUP)
                copy.cards.forEach { card ->
                    // Preserve ordering and historical duplicates; never merge differently named groups.
                    if (command.name in memberships.getValue(card.collectionItemId)) {
                        for (index in card.groups.indices) {
                            if (card.groups[index] == command.name) card.groups[index] = command.newName
                        }
                    }
                }
            }
            is LibraryMutation.DeleteGroup -> {
                if (!command.confirmed) return reject(MutationFailure.CONFIRMATION_REQUIRED)
                if (!groupExists(command.name)) return reject(MutationFailure.MISSING_GROUP)
                copy.cards.filter { command.name in memberships.getValue(it.collectionItemId) }
                    .forEach { it.groups.removeAll { group -> group == command.name } }
            }
            is LibraryMutation.RemoveFromGroup -> {
                if (command.cardIds.isEmpty()) return reject(MutationFailure.INVALID_INPUT)
                if (!groupExists(command.name)) return reject(MutationFailure.MISSING_GROUP)
                if (command.cardIds.any { it !in byId }) return reject(MutationFailure.MISSING_CARD)
                command.cardIds.filter { command.name in memberships.getValue(it) }
                    .forEach { byId.getValue(it).groups.removeAll { group -> group == command.name } }
            }
        }
        return PreparedLibraryMutation.Ready(copy, deleted)
    }

    private fun detach(source: Biblio): Biblio {
        val bytes = ByteArrayOutputStream().also { stream ->
            ObjectOutputStream(stream).use { it.writeObject(source) }
        }.toByteArray()
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as Biblio }
    }
}
