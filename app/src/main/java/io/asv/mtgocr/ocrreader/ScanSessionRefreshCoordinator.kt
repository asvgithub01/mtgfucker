package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.CardInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Locale

/** Dispatches session refreshes from Kotlin coroutines while the Java activity remains legacy. */
class ScanSessionRefreshCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    interface Task {
        fun isIncomplete(card: CardInfo): Boolean
        fun refresh(card: CardInfo, forcePriceRefresh: Boolean)
    }

    fun refreshAll(cards: List<CardInfo>, task: Task) = launch(cards, false, false, task)

    fun refreshMissing(cards: List<CardInfo>, task: Task) = launch(cards, true, false, task)

    private fun launch(
        cards: List<CardInfo>,
        missingOnly: Boolean,
        forcePriceRefresh: Boolean,
        task: Task
    ) {
        scope.launch {
            cards.toList()
                .filter { !missingOnly || task.isIncomplete(it) }
                .distinctBy(::refreshKey)
                .forEach { card ->
                    task.refresh(card, forcePriceRefresh)
                    yield()
                }
        }
    }

    private fun refreshKey(card: CardInfo): String {
        val language = card.languageCode.trim().lowercase(Locale.ROOT)
        return card.printingUuid?.trim()?.takeIf { it.isNotEmpty() }?.let { uuid ->
            "$uuid|${card.finish.orEmpty().trim().lowercase(Locale.ROOT)}|$language"
        } ?: run {
            "${card.name.orEmpty().trim().lowercase(Locale.ROOT)}|$language"
        }
    }

    fun close() = scope.cancel()
}
