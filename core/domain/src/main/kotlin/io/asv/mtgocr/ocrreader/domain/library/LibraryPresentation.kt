package io.asv.mtgocr.ocrreader.domain.library

import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import java.text.Normalizer
import java.util.Locale

enum class LibrarySection { CARDS, GROUPS, DECKS }
enum class LibrarySort { NEWEST, NAME }
data class MembershipSummary(val name: String, val copies: Long, val sideboard: Long)

/** Pure display policies. Never normalize identities or rewrite group membership. */
object LibraryPresentation {
    fun rows(
        snapshot: LibraryShellSnapshot,
        section: LibrarySection,
        membership: String?,
        query: String,
        sort: LibrarySort
    ): List<CollectionRow> {
        val needle = normalize(query)
        val filtered = snapshot.collection.rows.filter { row ->
            val matchesMembership = membership == null || when (section) {
                LibrarySection.CARDS -> true
                LibrarySection.GROUPS -> membership in row.groups
                LibrarySection.DECKS -> membership in row.decks
            }
            matchesMembership && normalize("${row.name} ${row.setName} ${row.setCode}").contains(needle)
        }
        return when (sort) {
            LibrarySort.NEWEST -> filtered.sortedByDescending { it.addedAt }
            LibrarySort.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    fun memberships(snapshot: LibraryShellSnapshot, section: LibrarySection): List<MembershipSummary> {
        val rows = snapshot.collection.rows
        val names = when (section) {
            LibrarySection.CARDS -> emptyList()
            LibrarySection.GROUPS -> rows.flatMap { it.groups }
            LibrarySection.DECKS -> snapshot.decks.map { it.name } + rows.flatMap { it.decks }
        }
        // Exact names matter: do not merge differently cased groups and lose membership.
        return names.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER).map { name ->
            val members = rows.filter { name in if (section == LibrarySection.DECKS) it.decks else it.groups }
            MembershipSummary(name, copies(members),
                if (section == LibrarySection.DECKS)
                    copies(members.filter { name in it.sideboardDecks }) else 0)
        }
    }

    fun copies(rows: List<CollectionRow>): Long = rows.sumOf { it.quantity.toLong() }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
}
