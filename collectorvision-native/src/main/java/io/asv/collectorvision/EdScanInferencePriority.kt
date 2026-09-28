package io.asv.collectorvision

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Suspend heavy postprocessing while a live scanner owns the camera; queued work is not lost. */
object EdScanInferencePriority {
    private val owners=mutableSetOf<Any>()
    private val live=MutableStateFlow(false)
    @Synchronized fun acquire(owner: Any) { owners.add(owner); live.value=owners.isNotEmpty() }
    @Synchronized fun release(owner: Any) { owners.remove(owner); live.value=owners.isNotEmpty() }
    suspend fun awaitIdle() { live.first { !it } }
}
