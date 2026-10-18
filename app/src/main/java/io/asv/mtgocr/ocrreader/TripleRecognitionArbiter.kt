package io.asv.mtgocr.ocrreader

/** Main-thread arbitration. Votes are fresh observations, never counts of AR render callbacks. */
internal class TripleRecognitionArbiter {
    data class Decision(val accepted: Boolean, val newEncounter: Boolean=false, val reason: String)
    private data class Evidence(val id: String, val since: Long, var at: Long, var samples: Int, var strong: Boolean)
    private val evidence=mutableMapOf<String,Evidence>()
    private var identity=""
    private var authority=""
    private var newScene=true
    private var afterAbsence=false
    private var committedAt: Long?=null
    private var committedSampleAt=0L
    private var missingSince: Long?=null
    private var missingFrames=0
    private var reference: FloatArray?=null
    private var changed: FloatArray?=null
    private var changedAt=0L
    private var changedFrames=0
    @Volatile var scene=0L; private set
    @Volatile var sceneAt=0L; private set
    var lastReason="idle"; private set
    val hasConfirmedScene: Boolean get() = !newScene
    val needsConfirmation: Boolean get() = newScene || lastReason !in setOf("accepted","same_identity")

    /** Restart is not proof of another physical copy. Fresh evidence may retain the last row. */
    fun restoreCurrent(id:String,source:String) {
        resetPending();identity=id;authority=source;newScene=false;afterAbsence=false;committedAt=null
    }

    /** Explicit user action arms a NEW row, even for the same identity; old captures cannot vote. */
    fun beginManualScan(now:Long) {
        identity="";committedAt=null;committedSampleAt=0
        resetPending();startScene(now)
    }

    fun resetPending() { evidence.clear();authority="";newScene=identity.isBlank();afterAbsence=false;missingSince=null;missingFrames=0;reference=null;changed=null;changedFrames=0 }
    fun present() { missingSince=null;missingFrames=0 }
    fun missing(now: Long) {
        if(missingSince==null) missingSince=now
        missingFrames++
        if(missingFrames>=3 && now-requireNotNull(missingSince)>=1500 && !afterAbsence) startScene(now,true)
    }
    private fun startScene(now: Long, absent: Boolean=false, preserveEvidence: Boolean=false) {
        scene++;sceneAt=now;newScene=true;afterAbsence=absent;reference=null;changed=null;changedFrames=0
        if(!preserveEvidence) evidence.clear()
    }
    /** Rectified appearance from ONE producer per mode; disagreement between engines is not motion. */
    fun frame(signature: FloatArray?, now: Long) {
        if(signature==null || signature.any { !it.isFinite() } || now<sceneAt) return
        present()
        val base=reference
        if(base==null) { reference=signature;return }
        if(distance(base,signature)<.20f) { changed=null;changedFrames=0;return }
        if(changed==null || distance(requireNotNull(changed),signature)>.08f) {
            changed=signature;changedAt=now;changedFrames=1;return
        }
        changedFrames++
        if(changedFrames>=2 && now-changedAt>=400) {
            // Appearance is not identity: keep each engine's bounded candidate history.
            // sceneAt still rejects callbacks captured before this boundary. Absence never does this.
            startScene(now,afterAbsence,preserveEvidence=true);reference=signature
        }
    }
    /** A confirmed language change is a new physical copy, not a correction of the old row. */
    fun separateLanguageCopy(sampleAt:Long, now:Long) {
        startScene(sampleAt);newScene=false;afterAbsence=false;authority="OCR"
        committedAt=now;committedSampleAt=sampleAt
    }
    fun observe(id: String, source: String, strong: Boolean, sampleAt: Long, now: Long, pairedVisual:Boolean=false,
                requireIndependentAr:Boolean=false): Decision {
        fun reject(reason: String): Decision { lastReason=reason;return Decision(false,reason=reason) }
        if(id.isBlank() || sampleAt<sceneAt || now-sampleAt>2500 || sampleAt>now) return reject("stale")
        val old=evidence[source]
        if(old!=null && sampleAt<old.at) return reject("out_of_order")
        val item=if(old?.id==id && sampleAt-old.at<=(if(source=="OCR") TripleScanPolicy.EVIDENCE_GAP_MS else 2500)) old.apply {
            if(sampleAt>at) samples++
            at=sampleAt;this.strong=strong
        } else Evidence(id,sampleAt,sampleAt,1,strong).also { evidence[source]=it }
        val stable=when(source) {
            "EdScan" -> strong
            else -> item.samples>=2 && sampleAt-item.since>=600
        }
        // Repeated FULL_TRACKING callbacks are ONE recognizer, not independent votes.
        // In mixed mode AR may track an accepted card, but cannot introduce a new one
        // (or consume an appearance/absence boundary) without a fresh independent read.
        if(source=="AR" && requireIndependentAr && (identity!=id || newScene || afterAbsence)) {
            val corroborated=evidence.any { (engine, vote) ->
                engine!="AR" && vote.id==id && vote.at>=sceneAt && vote.at>=item.since &&
                    now-vote.at<=2500 && (if(engine=="EdScan") vote.strong else vote.samples>=2 && vote.at-vote.since>=600)
            }
            if(!corroborated) return reject("ar_unverified")
        }
        // Identity and physical copies are separate. A noisy fingerprint must not strand
        // AR-only mode waiting for a disabled recognizer to confirm the same card.
        if(identity==id && !afterAbsence) {
            if(source=="EdScan" && strong) authority="EdScan"
            newScene=false;lastReason="same_identity";return Decision(true,reason=lastReason)
        }
        if(!stable) return reject(if(source=="EdScan") "visual_unconfirmed" else "stability")
        val art=evidence["EdScan"]
        val ar=evidence["AR"]
        // Only competing observations captured DURING this candidate can veto it.
        // The winner from a previous card is history, never an indefinite lock.
        if(source!="EdScan" && art!=null && art.strong && art.id!=id &&
            art.at>=item.since && now-art.at<=2500) return reject("edscan_priority")
        if(source=="OCR" && !requireIndependentAr && ar!=null && ar.id!=id && ar.samples>=2 && ar.at-ar.since>=600 &&
            ar.at>=item.since && now-ar.at<=1000) return reject("visual_priority")

        // Correct an AR hypothesis only using a capture made BEFORE that acceptance.
        // A later capture of a different card must create a new row, never replace it.
        val correcting=!newScene && identity!=id && authority=="AR" && source=="EdScan" && strong &&
            sampleAt<=committedSampleAt && committedAt?.let { now-it<=2500 }==true
        val isNew=!correcting && (identity.isBlank() || identity!=id || afterAbsence)
        if(isNew && committedAt?.let { now-it<600 }==true) return reject("copy_interval")
        if(isNew) {
            if(!newScene) { startScene(sampleAt);evidence[source]=item }
            committedAt=now;committedSampleAt=sampleAt
        }
        identity=id;authority=source;newScene=false;afterAbsence=false;lastReason=if(correcting) "corrected_capture" else "accepted"
        return Decision(true,isNew,lastReason)
    }

    companion object {
        fun distance(a: FloatArray,b: FloatArray): Float {
            if(a.size!=b.size || a.isEmpty()) return 1f
            return kotlin.math.sqrt(a.indices.sumOf { val d=(a[it]-b[it]).toDouble();d*d }/a.size).toFloat()
        }
    }
}
