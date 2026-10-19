package io.asv.mtgocr.ocrreader

import android.content.Context

/** Membership is separate from photo records so an analysis callback cannot undo grouping. */
object PhotoGroups {
    private fun prefs(c:Context)=c.getSharedPreferences("photo_groups",0)
    fun key(c:Context,id:String)=prefs(c).getString(id,id) ?: id
    fun members(c:Context,id:String):List<PhotoScanEntry> {
        val key=key(c,id)
        return PhotoScanStore.entries(c).filter { key(c,it.id)==key }.sortedBy { it.createdAt }
    }
    @Synchronized fun join(c:Context,ids:List<String>) {
        if(ids.isEmpty())return
        val keys=ids.map { key(c,it) }.toSet()
        val group=key(c,ids.first())
        val edit=prefs(c).edit()
        PhotoScanStore.entries(c).filter { key(c,it.id) in keys }.forEach { edit.putString(it.id,group) }
        check(edit.commit())
    }
    fun rows(c:Context,entries:List<PhotoScanEntry>)=entries.groupBy { key(c,it.id) }.values.map { group ->
        val first=group.minBy { it.createdAt }
        first.copy(cards=group.flatMap { it.cards }.toMutableList(),state=when {
            group.any { it.state==PhotoScanEntry.STATE_ANALYZING } -> PhotoScanEntry.STATE_ANALYZING
            group.any { it.state==PhotoScanEntry.STATE_ERROR } -> PhotoScanEntry.STATE_ERROR
            else -> PhotoScanEntry.STATE_READY
        })
    }
}
