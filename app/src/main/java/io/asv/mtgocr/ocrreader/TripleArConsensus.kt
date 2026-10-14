package io.asv.mtgocr.ocrreader

/** Exact-printing agreement, not a second vote from repeated AR render callbacks. */
internal class TripleArConsensus {
    private var id:String?=null
    private var since=0L
    private var last=0L
    @Synchronized fun reset() { id=null;since=0;last=0 }
    @Synchronized fun observe(next:String?,now:Long) {
        if(next!=id || now-last>350) { id=next;since=now }
        last=now
    }
    @Synchronized fun confirms(next:String?,score:Float?,runner:Float?,sampleAt:Long,now:Long):Boolean =
        next!=null && next==id && sampleAt>=since && now>=sampleAt && now-sampleAt<=2500 &&
            now-last in 0..350 && now-since>=600 && score!=null && score.isFinite() && score>=.60f &&
            runner!=null && runner.isFinite() && score-runner>=.08f
}
