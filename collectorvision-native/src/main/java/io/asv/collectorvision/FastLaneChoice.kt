package io.asv.collectorvision

/** Separate embedding distributions: never choose a strong identity by raw cross-lane score. */
internal object FastLaneChoice {
    data class Choice(val full:Boolean,val conflict:Boolean)
    fun choose(fullStrong:Boolean,cropStrong:Boolean,sameIdentity:Boolean,fullSeparated:Boolean=false)=
        Choice(fullStrong || (!cropStrong && fullSeparated),fullStrong && cropStrong && !sameIdentity)
}
