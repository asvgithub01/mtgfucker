package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.EdScanPerf
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.ar.core.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One-card, local Augmented Images POC. Never writes to the collection. */
class TripleArPreview(private val host: AppCompatActivity, private var intent: android.content.Intent,
    private val wantsFrame: ()->Boolean = { false },
    private val onCpuFrame: (android.media.Image,Int,()->Unit,String?,FloatArray?,FloatArray?)->Boolean = { _,_,_,_,_,_->false }, private val optionsContainer: LinearLayout?=null) : FrameLayout(host), androidx.lifecycle.DefaultLifecycleObserver {
    private fun getString(id: Int,vararg args: Any)=host.getString(id,*args)
    private fun runOnUiThread(action: ()->Unit)=host.runOnUiThread(action)
    private val applicationContext get()=host.applicationContext
    private var priceObserverCleanup: (() -> Unit)?=null
    private lateinit var camera: TripleArSurface
    private lateinit var titleGuide:TripleTitleGuide
    fun enableEdScanContour(enabled:Boolean) { camera.edscanContourEnabled=enabled }
    fun edscanProjection(timestamp:Long)=camera.contourProjection?.takeIf { it.timestamp==timestamp }
    fun showGuidedName(name:String?) { titleGuide.showName(name) }
    private lateinit var imageCache:TripleArImageCache
    private var imageLanguages=emptyMap<String,String>()
    private var imageLayerEnabled=false
    private val imageDispatchPending=java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastImageDispatch=0L
    fun setImageLanguage(printing:String,language:String) {
        if(imageLanguages[printing]==language) return
        imageLanguages=imageLanguages+(printing to language)
        overlay.languages=imageLanguages;overlay.invalidate()
        requestVisibleImages()
    }
    private fun requestVisibleImages() {
        if(destroyed || !imageLayerEnabled) return
        val visible=overlay.cards.mapNotNull { card -> camera.preloadedCards[card.printing] ?: intent.takeIf { it.getStringExtra("printing")==card.printing }?.let {
            ArPreloadCard(card.printing,it.getStringExtra("image").orEmpty(),"",card.price)
        } }
        imageCache.request(visible,imageLanguages)
    }
    private lateinit var overlay: TripleArOverlay
    private lateinit var status: TextView
    private lateinit var batchProgress: ProgressBar
    private var batchStartedAt=0L
    private var compiledBatch: java.io.File?=null
    private lateinit var retry: Button
    private val worker=Executors.newSingleThreadExecutor()
    private val effectLoader=Executors.newSingleThreadExecutor()
    private var revealRequested=""
    private var session: Session?=null
    @Volatile private var batchMode=false
    private var pendingBatch: Pair<String,List<ArPreloadCard>>?=null
    private var batchRequestedGeneration=-1
    @Volatile private var batchReadyCount=0
    private var configured=false
    @Volatile private var targetGeneration=0
    private var requestedGeneration=-1
    // Worker-confined, session-scoped: never share native databases across Sessions.
    private val referenceCache=object: LinkedHashMap<Pair<String,String>,AugmentedImageDatabase>(4,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String,String>,AugmentedImageDatabase>?)=size>4
    }
    private val diskCache=ArReferenceDiskCache(java.io.File(host.cacheDir,"ar-references"))
    @Volatile private var warming=false
    @Volatile private var diskReferenceHits=0
    private var resumed=false
    private var running=false
    private var installRequested=false
    @Volatile private var destroyed=false
    init {
        val root=FrameLayout(host)
        status=TextView(host).apply { setText(R.string.edscan_ar_loading); setPadding(16,8,16,8) }
        val stage=FrameLayout(host)
        overlay=TripleArOverlay(host).apply { price=intent.getStringExtra("price").orEmpty() }
        camera=TripleArSurface(host, { points -> val wasVisible=overlay.points!=null; overlay.points=points; if(wasVisible || points!=null) overlay.postInvalidateOnAnimation() }, { _ -> /* Tracking is represented by the card overlay, not a banner. */ }, { error -> runOnUiThread { if(!destroyed) { stopAr(); fail(error) } } },wantsFrame,onCpuFrame,{ points ->
            val had=overlay.genericPoints!=null
            overlay.genericPoints=points
            if(had || points!=null) overlay.postInvalidateOnAnimation()
        },{ card ->
            overlay.borderColor=camera.rarityColors[card.printing] ?: TripleRarityColor.color(card.rarity)
            overlay.price=card.priceText
            runOnUiThread {
                if(!destroyed) {
                    targetGeneration++; revealRequested=""
                    intent=android.content.Intent().putExtra("printing",card.printing).putExtra("image",card.image).putExtra("price",card.priceText)
                    loadRevealTexture()
                }
            }
        })
        imageCache=TripleArImageCache(host,{ images,loading -> overlay.images=images;overlay.loading=loading;overlay.invalidate() },{ diagnostics(it) })
        camera.onVisibleCards={ cards ->
            overlay.cards=cards;overlay.postInvalidateOnAnimation()
            val now=android.os.SystemClock.elapsedRealtime()
            if(now-lastRecognitionDispatch>=100 && recognitionDispatchPending.compareAndSet(false,true)) {
                lastRecognitionDispatch=now
                runOnUiThread {
                    recognitionDispatchPending.set(false)
                    if(!destroyed) {
                        val printing=cards.firstOrNull()?.printing
                        if(printing!=lastRecognizedPrinting) {
                            diagnostics("triple.arTracking visible=${printing!=null} target=${printing?.hashCode()} sinceEnabledMs=${android.os.SystemClock.elapsedRealtime()-recognitionStartedAt}")
                            lastRecognizedPrinting=printing
                        }
                        onRecognition(printing)
                        onCardPosition(cards.firstOrNull()?.points)
                    }
                }
            }
            if(now-lastImageDispatch>=500 && imageDispatchPending.compareAndSet(false,true)) {
                lastImageDispatch=now
                runOnUiThread { imageDispatchPending.set(false);if(!destroyed) requestVisibleImages() }
            }
        }
        stage.addView(camera,FrameLayout.LayoutParams(-1,-1))
        stage.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        titleGuide=TripleTitleGuide(host).apply { tag="triple_title_guide" }
        stage.addView(titleGuide,FrameLayout.LayoutParams(-1,-1))
        root.addView(stage,LayoutParams(-1,-1))
        status.apply { textSize=12f; maxLines=4; setTextColor(android.graphics.Color.WHITE); setBackgroundColor(0xB0000000.toInt()) }
        root.addView(status,LayoutParams(-1,-2,android.view.Gravity.TOP))
        batchProgress=ProgressBar(host).apply { visibility=View.GONE; tag="ar_database_loading" }
        root.addView(batchProgress,LayoutParams(-2,-2,android.view.Gravity.CENTER))
        val controls=LinearLayout(host).apply { orientation=LinearLayout.VERTICAL }
        val preferences=host.getSharedPreferences("triple_ar",android.content.Context.MODE_PRIVATE)
        camera.energyBorderEnabled=preferences.getBoolean("energy_border",true)
        controls.addView(CheckBox(host).apply {
            tag="ar_energy_border"
            setText(R.string.edscan_ar_energy_border)
            setTextColor(android.graphics.Color.WHITE)
            isChecked=camera.energyBorderEnabled
            setOnCheckedChangeListener { _,enabled ->
                camera.energyBorderEnabled=enabled
                preferences.edit().putBoolean("energy_border",enabled).apply()
            }
        })
        fun option(key: String,label: Int,initial: (Boolean)->Unit) {
            val value=preferences.getBoolean(key,false); initial(value)
            controls.addView(CheckBox(host).apply {
                tag="ar_$key"; setText(label); setTextColor(android.graphics.Color.WHITE); isChecked=value
                setOnCheckedChangeListener { _,enabled -> initial(enabled); preferences.edit().putBoolean(key,enabled).apply() }
            })
        }
        val guideEnabled=preferences.getBoolean("title_guide",true)
        camera.titleGuideEnabled=guideEnabled;titleGuide.visibility=if(guideEnabled) View.VISIBLE else View.GONE
        controls.addView(CheckBox(host).apply {
            setText(R.string.triple_title_guide);isChecked=guideEnabled
            setOnCheckedChangeListener { _,enabled ->
                camera.titleGuideEnabled=enabled;titleGuide.visibility=if(enabled) View.VISIBLE else View.GONE
                titleGuide.showName(null);preferences.edit().putBoolean("title_guide",enabled).apply()
            }
        })
        option("multiple_cards",R.string.triple_ar_multiple) { camera.multipleCardsEnabled=it }
        option("image_layer",R.string.triple_ar_image_layer) {
            imageLayerEnabled=it;overlay.imageEnabled=it
            if(it) requestVisibleImages() else imageCache.clear()
            overlay.invalidate()
        }
        option("generic_outline",R.string.edscan_ar_generic_outline) { camera.genericOutlineEnabled=it }
        option("confetti",R.string.triple_confetti) { camera.confettiEnabled=it }
        option("reveal",R.string.edscan_ar_reveal) { camera.revealEnabled=it; if(it) loadRevealTexture() }
        retry=Button(host).apply { setText(R.string.edscan_ar_retry); visibility=View.GONE; setOnClickListener { if(batchMode) { batchStartedAt=android.os.SystemClock.elapsedRealtime(); batchProgress.visibility=View.VISIBLE; batchRequestedGeneration=-1; loadPreloadedDatabase() } else if(configured && intent.hasExtra("printing")) updateTarget(intent) else prepareAr() } }
        controls.addView(retry)
        controls.setBackgroundColor(0xB0000000.toInt())
        if(optionsContainer!=null) optionsContainer.addView(controls) else root.addView(controls,LayoutParams(-1,-2,android.view.Gravity.BOTTOM))
        addView(root,LayoutParams(-1,-1))
        host.lifecycle.addObserver(this)
    }

    fun updateTarget(data: android.content.Intent) {
        if(destroyed || batchMode) return
        EdScanPerf.event("AR.targetRequested")
        val reuse=ArReferencePolicy.canReuse(intent.getStringExtra("printing"),intent.getStringExtra("image"),
            data.getStringExtra("printing"),data.getStringExtra("image"),requestedGeneration==targetGeneration)
        intent=data
        if(!reuse) {
            targetGeneration++; revealRequested=""
            camera.targetName=""; overlay.points=null
        }
        camera.targetColor=TripleRarityColor.color(data.getStringExtra("rarity"));overlay.borderColor=camera.targetColor
        overlay.price=data.getStringExtra("price").orEmpty();camera.primaryPrice=overlay.price; overlay.invalidate()
        if(reuse) EdScanPerf.event("AR.referenceReusedActive")
        priceObserverCleanup?.invoke(); priceObserverCleanup=null
        refreshPendingPrice()
        loadTarget()
        loadRevealTexture()
    }
    private fun loadRevealTexture() {
        if(!camera.revealEnabled || destroyed) return
        val data=intent; val printing=data.getStringExtra("printing").orEmpty()
        val url=data.getStringExtra("image").orEmpty(); val token=targetGeneration
        val key="$printing|$url"
        if(printing.isBlank() || url.isBlank() || key==revealRequested) return
        revealRequested=key
        effectLoader.execute {
            val request=Glide.with(applicationContext).load(url).asBitmap().into(512,716)
            val bitmap=try { request.get(15,TimeUnit.SECONDS).copy(android.graphics.Bitmap.Config.ARGB_8888,false) }
                catch(_: Exception) { null }
                finally { runOnUiThread { Glide.clear(request) } }
            runOnUiThread {
                if(destroyed || token!=targetGeneration) { bitmap?.recycle(); if(revealRequested==key) revealRequested=""; return@runOnUiThread }
                if(bitmap!=null) camera.setRevealImage(printing,bitmap) else revealRequested=""
            }
        }
    }
    /** CPU preparation only. Does not activate a database, expose a price, or change target. */
    fun prepareReference(printing: String,url: String) {
        val ar=session ?: return
        if(destroyed || batchMode || !running || warming) return
        warming=true
        worker.execute {
            try {
                if(!destroyed) {
                    EdScanPerf.event("AR.prewarmStarted")
                    referenceDatabase(ar,printing,url,speculative=true)
                    EdScanPerf.event("AR.prewarmReady")
                }
            } catch(_: Exception) { /* Speculative failures must not affect accepted scans. */ }
            finally { warming=false }
        }
    }
    private fun referenceDatabase(ar: Session,printing: String,url: String,speculative: Boolean=false): AugmentedImageDatabase {
        val key=printing to url
        referenceCache[key]?.let { EdScanPerf.event("AR.referenceCacheHit"); return it }
        diskCache.read(printing,url)?.let { bytes ->
            runCatching {
                EdScanPerf.once("AR.referenceDeserialize") { bytes.inputStream().use { AugmentedImageDatabase.deserialize(ar,it) } }
            }.onSuccess { database ->
                referenceCache[key]=database
                diskReferenceHits++
                EdScanPerf.event("AR.referenceDiskHit")
                return database
            }.onFailure { diskCache.discard(printing,url) }
        }
        val request=Glide.with(applicationContext).load(url).asBitmap().into(512,716)
        val database=try {
            val bitmap=EdScanPerf.once("AR.referenceImage") { if(speculative) request.get(250,TimeUnit.MILLISECONDS) else request.get(45,TimeUnit.SECONDS) }
            check(!destroyed)
            EdScanPerf.once("AR.referenceBuild") {
                AugmentedImageDatabase(ar).also { it.addImage(printing,bitmap,0.063f) }
            }
        } finally { runOnUiThread { Glide.clear(request) } }
        referenceCache[key]=database
        // Persistence is queued AFTER preparation; do not delay activation on disk I/O.
        if(!destroyed) runCatching { worker.execute {
            if(!destroyed) runCatching {
                val bytes=java.io.ByteArrayOutputStream().also { database.serialize(it) }.toByteArray()
                diskCache.write(printing,url,bytes)
            }
        } }
        return database
    }
    private var lastRecognitionDispatch=0L
    private val recognitionDispatchPending=java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastRecognizedPrinting:String?=null
    private var recognitionStartedAt=android.os.SystemClock.elapsedRealtime()
    var onRecognition:(String?)->Unit = {}
    var onCardPosition:(FloatArray?)->Unit = {}
    fun setRecognitionEnabled(enabled:Boolean) { camera.recognitionEnabled=enabled;recognitionStartedAt=android.os.SystemClock.elapsedRealtime();lastRecognizedPrinting=null }
    var diagnostics:(String)->Unit = { EdScanPerf.event(it) }
    @Volatile private var batchEpoch=0
    private val fallbackCards=linkedMapOf<String,ArPreloadCard>()
    private val pendingReferences=linkedMapOf<String,ArPreloadCard>()
    private var sessionBytes:ByteArray?=null
    private var appendBusy=false
    private var capacityShown=false
    /** Session union: add one trained image to a clone of the last successful DB, not a replacement. */
    fun addFallbackReference(card:ArPreloadCard) {
        if(destroyed || card.printing in fallbackCards || card.printing in pendingReferences || pendingBatch?.second?.any { it.printing==card.printing }==true) return
        val base=pendingBatch?.second?.size ?: 0
        if(!TripleOcrPrintingPolicy.hasCapacity(base,fallbackCards.size,pendingReferences.size)) {
            diagnostics("triple.sessionDbFull count=1000")
            if(!capacityShown) { capacityShown=true;android.widget.Toast.makeText(host,R.string.triple_ar_capacity,android.widget.Toast.LENGTH_LONG).show() }
            return
        }
        pendingReferences[card.printing]=card
        drainSessionReferences()
    }
    private fun drainSessionReferences() {
        val ar=session ?: return
        if(destroyed || appendBusy || pendingReferences.isEmpty() || (batchMode && batchReadyCount==0)) return
        val card=pendingReferences.values.first();val epoch=batchEpoch
        val previous=sessionBytes;val file=compiledBatch
        val base=pendingBatch?.second.orEmpty();val prior=fallbackCards.values.toList()
        appendBusy=true
        worker.execute {
            val started=System.nanoTime()
            val result=runCatching {
                check(!destroyed && epoch==batchEpoch)
                val database=when {
                    previous!=null -> previous.inputStream().use { AugmentedImageDatabase.deserialize(ar,it) }
                    file!=null -> file.inputStream().use { AugmentedImageDatabase.deserialize(ar,it) }
                    else -> AugmentedImageDatabase(ar)
                }
                require(database.numImages<TripleOcrPrintingPolicy.MAX_AR_REFERENCES)
                val request=Glide.with(applicationContext).load(card.image).asBitmap().into(512,716)
                try { database.addImage(card.printing,request.get(3,TimeUnit.SECONDS),.063f) }
                finally { runOnUiThread { Glide.clear(request) } }
                val bytes=java.io.ByteArrayOutputStream().also { database.serialize(it) }.toByteArray()
                database to bytes
            }
            result.onSuccess { (database,bytes) -> camera.queueEvent {
                if(!destroyed && epoch==batchEpoch) {
                    val applied=runCatching {
                        val config=ar.config;config.augmentedImageDatabase=database;ar.configure(config)
                        camera.preloadMode=true
                        camera.preloadedCards=(base+prior+card).associateBy { it.printing }
                        camera.preferredTargetName=card.printing
                        batchReadyCount=database.numImages
                    }
                    runOnUiThread {
                        if(!destroyed && epoch==batchEpoch) {
                            if(applied.isSuccess) {
                                sessionBytes=bytes
                                val latest=pendingReferences[card.printing] ?: card
                                fallbackCards[card.printing]=latest
                                camera.preloadedCards=(pendingBatch?.second.orEmpty()+fallbackCards.values).associateBy { it.printing }
                                if(camera.targetName==card.printing) { overlay.price=latest.priceText;overlay.invalidate() }
                            }
                            diagnostics("triple.sessionAppend ok=${applied.isSuccess} count=${database.numImages} ms=${(System.nanoTime()-started)/1000000}")
                            pendingReferences.remove(card.printing);appendBusy=false;drainSessionReferences()
                        }
                    }
                }
            } }.onFailure { error -> runOnUiThread {
                if(!destroyed && epoch==batchEpoch) {
                    diagnostics("triple.sessionAppend ok=false type=${error.javaClass.simpleName}")
                    pendingReferences.remove(card.printing);appendBusy=false;drainSessionReferences()
                }
            } }
        }
    }
    fun refreshSessionCard(card:ArPreloadCard) {
        if(card.printing in fallbackCards) fallbackCards[card.printing]=card
        if(card.printing in pendingReferences) pendingReferences[card.printing]=card
        if(card.printing in camera.preloadedCards) camera.preloadedCards=camera.preloadedCards+(card.printing to card)
        if(camera.targetName==card.printing) { overlay.price=card.priceText;overlay.invalidate() }
    }
    fun updateRarity(printing:String,rarity:String) {
        val color=TripleRarityColor.color(rarity)
        camera.rarityColors=camera.rarityColors+(printing to color)
        if(camera.targetName==printing) { camera.targetColor=color;overlay.borderColor=color;overlay.invalidate() }
    }
    fun updatePrices(cards:List<ArPreloadCard>) {
        if(destroyed || !batchMode) return
        pendingBatch=pendingBatch?.let { it.first to cards }
        camera.preloadedCards=(cards+fallbackCards.values).associateBy { it.printing }
        cards.firstOrNull { it.printing==camera.targetName }?.let { overlay.price=it.priceText;overlay.invalidate() }
    }
    fun setPreloadedBatch(group: String?,cards: List<ArPreloadCard>,startedAt: Long=android.os.SystemClock.elapsedRealtime(),compiled: java.io.File?=null) {
        if(group==null) intent=android.content.Intent()
        batchEpoch++;fallbackCards.clear();pendingReferences.clear();sessionBytes=null;appendBusy=false;capacityShown=false;camera.preferredTargetName="";camera.rarityColors=emptyMap()
        compiledBatch=compiled
        batchStartedAt=startedAt
        batchProgress.visibility=if(group!=null) View.VISIBLE else View.GONE
        retry.visibility=View.GONE
        if(destroyed) return
        batchMode=group!=null; targetGeneration++; batchReadyCount=0
        pendingBatch=group?.let { it to cards }
        camera.preloadMode=batchMode; camera.preloadedCards=emptyMap(); camera.targetName=""
        overlay.cards=emptyList();imageCache.clear()
        overlay.points=null; overlay.genericPoints=null; overlay.price=""; overlay.invalidate()
        priceObserverCleanup?.invoke(); priceObserverCleanup=null
        revealRequested=""
        if(batchMode) {
            status.visibility=View.VISIBLE; status.setText(R.string.edscan_ar_preload_loading)
            loadPreloadedDatabase()
        } else {
            status.visibility=View.GONE;batchProgress.visibility=View.GONE
            val token=targetGeneration
            camera.queueEvent {
                if(!destroyed && token==targetGeneration) session?.let { ar ->
                    runCatching { val config=ar.config;config.augmentedImageDatabase=AugmentedImageDatabase(ar);ar.configure(config) }
                        .onFailure { android.util.Log.w("EdScanPerf","Triple clear DB failed",it) }
                }
            }
        }
    }
    private fun loadPreloadedDatabase() {
        val ar=session ?: return
        val batch=pendingBatch ?: return
        val token=targetGeneration
        if(destroyed || !batchMode || batchRequestedGeneration==token) return
        if(batch.second.isEmpty()) {
            if(batch.first.isNotBlank()) { batchProgress.visibility=View.GONE; status.visibility=View.VISIBLE; status.setText(R.string.edscan_ar_preload_empty) }
            return
        }
        batchRequestedGeneration=token
        val precompiled=compiledBatch
        worker.execute {
            val started=System.nanoTime()
            val entries=batch.second
            val cache=ArReferenceDiskCache(java.io.File(host.cacheDir,"ar-preloaded-batches"))
            val key=ArPreloadBatch.cacheIdentity(entries)
            var successes=entries
            val result=runCatching {
                require(entries.size<=1000) { "AR database exceeds 1000 references" }
                val cached=if(precompiled!=null) {
                    precompiled.inputStream().use { AugmentedImageDatabase.deserialize(ar,it) }.also {
                        require(it.numImages==entries.size) { "AR package count mismatch" }
                    }
                } else cache.read("batch-v1",key)?.let { bytes ->
                    runCatching { bytes.inputStream().use { AugmentedImageDatabase.deserialize(ar,it) } }
                        .getOrElse { cache.discard("batch-v1",key); null }
                }
                if(cached!=null && cached.numImages==entries.size) {
                    EdScanPerf.event("AR.batchDiskHit count=${entries.size}")
                    cached
                } else {
                    val database=AugmentedImageDatabase(ar)
                    val added=mutableListOf<ArPreloadCard>()
                    entries.forEachIndexed { index,card ->
                        check(!destroyed && batchMode && token==targetGeneration) { "Batch cancelled" }
                        val request=Glide.with(applicationContext).load(card.image).asBitmap().into(512,716)
                        try {
                            val bitmap=request.get(15,TimeUnit.SECONDS)
                            database.addImage(card.printing,bitmap,.063f)
                            added.add(card)
                        } catch(e: Exception) { EdScanPerf.event("AR.batchReferenceFailed index=$index type=${e.javaClass.simpleName}") }
                        finally { runOnUiThread { Glide.clear(request) } }
                        runOnUiThread {
                            if(!destroyed && token==targetGeneration) status.text=getString(R.string.edscan_ar_preload_progress,index+1,entries.size)
                        }
                    }
                    successes=added
                    check(added.isNotEmpty()) { getString(R.string.edscan_ar_preload_empty) }
                    if(added.size==entries.size) runCatching {
                        val bytes=java.io.ByteArrayOutputStream().also { database.serialize(it) }.toByteArray()
                        cache.write("batch-v1",key,bytes)
                    }
                    database
                }
            }
            result.onSuccess { database ->
                val valid=successes.associateBy { it.printing }
                camera.queueEvent {
                    if(!destroyed && batchMode && token==targetGeneration) try {
                        val config=ar.config; config.augmentedImageDatabase=database; ar.configure(config)
                        camera.preloadedCards=(pendingBatch?.second ?: successes).filter { it.printing in valid }.associateBy { it.printing }
                        batchReadyCount=valid.size
                        diagnostics("AR.batchReady count=${valid.size} totalMs=${(System.nanoTime()-started)/1_000_000}")
                        runOnUiThread {
                            if(!destroyed && batchMode && token==targetGeneration) {
                                drainSessionReferences()
                                batchProgress.visibility=View.GONE
                                val elapsed=android.os.SystemClock.elapsedRealtime()-batchStartedAt
                                EdScanPerf.event("AR.databaseReady count=${valid.size}/${entries.size} endToEndMs=$elapsed")
                                status.visibility=View.VISIBLE
                                status.text=getString(R.string.edscan_ar_preload_ready,batch.first,valid.size,entries.size)+"\n"+getString(R.string.edscan_ar_load_time,elapsed/1000.0)
                                retry.visibility=if(valid.size<entries.size) View.VISIBLE else View.GONE
                            }
                        }
                    } catch(e: Exception) { runOnUiThread { if(!destroyed) fail(e.message.orEmpty()) } }
                }
            }.onFailure { error -> runOnUiThread {
                if(!destroyed && batchMode && token==targetGeneration) { batchRequestedGeneration=-1; fail(error.message.orEmpty()) }
            } }
        }
    }
    private fun loadTarget() {
        if(batchMode) { loadPreloadedDatabase(); return }
        val ar=session ?: return
        val data=intent; val token=targetGeneration
        if(destroyed || requestedGeneration==token || !data.hasExtra("printing")) return
        val printing=data.getStringExtra("printing").orEmpty(); val url=data.getStringExtra("image").orEmpty()
        if(printing.isBlank() || url.isBlank()) { fail(getString(R.string.edscan_ar_no_image)); return }
        requestedGeneration=token
        val queuedAt=System.nanoTime()
        worker.execute {
            EdScanPerf.event("AR.referenceQueueMs=${(System.nanoTime()-queuedAt)/1_000_000}")
            if(destroyed || token!=targetGeneration) return@execute
            val result=runCatching { referenceDatabase(ar,printing,url) }
            if(!destroyed) result.onSuccess { database ->
                val configureQueued=System.nanoTime()
                camera.queueEvent {
                    EdScanPerf.event("AR.configureQueueMs=${(System.nanoTime()-configureQueued)/1_000_000}")
                    if(!destroyed && targetGeneration==token) try {
                        val config=ar.config; config.augmentedImageDatabase=database
                        val configuring=System.nanoTime()
                        ar.configure(config)
                        EdScanPerf.event("AR.configureMs=${(System.nanoTime()-configuring)/1_000_000} targetReadyMs=${(System.nanoTime()-queuedAt)/1_000_000}")
                        camera.targetName=printing
                    } catch(e: Exception) { runOnUiThread { if(!destroyed && targetGeneration==token) { requestedGeneration=-1; fail(e.message.orEmpty()) } } }
                }
            }.onFailure { error -> runOnUiThread { if(!destroyed && targetGeneration==token) { requestedGeneration=-1; fail(getString(R.string.edscan_ar_error,error.message.orEmpty())) } } }
        }
    }
    private fun refreshPendingPrice() {
        val dataIntent=intent; val token=targetGeneration
        if(!dataIntent.getBooleanExtra("pricePending",false)) return
        val id=dataIntent.getStringExtra("scryfallId").orEmpty()
        if(!id.matches(Regex("[a-fA-F0-9-]{36}"))) return
        val name=io.asv.collectorvision.EdScanJobs.price(host,id)
        val live=androidx.work.WorkManager.getInstance(host).getWorkInfosForUniqueWorkLiveData(name)
        val observer=object: androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> {
          override fun onChanged(infos: List<androidx.work.WorkInfo>) {
            if(infos.none { it.state.isFinished }) return
            live.removeObserver(this)
            if(destroyed) return
            worker.execute {
                val data=runCatching { org.json.JSONObject(java.io.File(io.asv.collectorvision.EdScanJobs.directory(host),"price-$id.json").readText())
                    .takeIf { it.optString("id").equals(id,true) } }.getOrNull()
                val currency=dataIntent.getStringExtra("currency").orEmpty().ifBlank { "eur" }
                val finish=dataIntent.getStringExtra("finish").orEmpty()
                val suffix=when(finish) { "foil" -> "_foil"; "etched" -> "_etched"; else -> "" }
                val amount=data?.optJSONObject("prices")?.optString(currency+suffix)?.toDoubleOrNull()?.takeIf { it.isFinite() && it>=0 }
                val value=amount?.let { io.asv.mtgocr.ocrreader.data.PriceCurrency.format(host,it,currency.uppercase()) }
                    ?: getString(io.asv.collectorvision.R.string.cv_native_price_unavailable)
                runOnUiThread { if(!destroyed && token==targetGeneration && intent===dataIntent) {
                    overlay.price=dataIntent.getStringExtra("label").orEmpty()+"\n"+value+" · Scryfall · "+finish
                    overlay.invalidate()
                } }
            }
          }
        }
        priceObserverCleanup={ live.removeObserver(observer) }
        live.observe(host,observer)
    }

    override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { resumed=true; prepareAr() }
    fun retryStart() { prepareAr() }
    private fun prepareAr() {
        if(!resumed || destroyed || running) return
        retry.visibility=View.GONE
        if(Build.VERSION.SDK_INT<24) { fail(getString(R.string.edscan_ar_unsupported)); return }
        if(ContextCompat.checkSelfPermission(host,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            fail(getString(R.string.edscan_ar_permission)); return
        }
        if(configured) { startAr(); return }
        try {
            val availability=ArCoreApk.getInstance().checkAvailability(host)
            if(availability.isTransient) {
                status.visibility=View.VISIBLE; status.setText(R.string.edscan_ar_loading)
                status.postDelayed({ if(resumed && !destroyed) prepareAr() },500); return
            }
            if(!availability.isSupported) { fail(getString(R.string.edscan_ar_unsupported)); return }
            if(ArCoreApk.getInstance().requestInstall(host,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested=true; return
            }
            val ar=session ?: EdScanPerf.once("UI.AR.sessionCreate") { Session(host) }.also { session=it }
            val configs=ar.getSupportedCameraConfigs(CameraConfigFilter(ar).setFacingDirection(CameraConfig.FacingDirection.BACK))
            configs.filter { it.imageSize.width*it.imageSize.height<=1280*960 }
                .maxByOrNull { it.imageSize.width*it.imageSize.height }?.let { ar.cameraConfig=it }
            val manager=host.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            camera.sensorRotation=manager.getCameraCharacteristics(ar.cameraConfig.cameraId)
                .get(android.hardware.camera2.CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            EdScanPerf.once("UI.AR.initialConfigure") { ar.configure(Config(ar).apply {
                focusMode=Config.FocusMode.AUTO
                planeFindingMode=Config.PlaneFindingMode.DISABLED
                updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE
            }) }
            EdScanPerf.event("AR.camera cpu=${ar.cameraConfig.imageSize} gpu=${ar.cameraConfig.textureSize} fps=${ar.cameraConfig.fpsRange}")
            configured=true; startAr(); loadTarget();drainSessionReferences()
        } catch(e: Exception) { fail(getString(R.string.edscan_ar_error,e.message.orEmpty())) }
    }
    private fun startAr() {
        if(!resumed || !configured || destroyed || running) return
        try {
            EdScanPerf.once("UI.AR.resume") { session?.resume() }
            camera.arSession=session
            camera.resetTracking(); camera.onResume(); running=true;drainSessionReferences()
            if(batchMode) {
                status.visibility=View.VISIBLE
                if(batchReadyCount==0) { status.setText(R.string.edscan_ar_preload_loading); loadPreloadedDatabase() }
            } else status.visibility=View.GONE
        } catch(e: Exception) { fail(getString(R.string.edscan_ar_error,e.message.orEmpty())) }
    }
    private fun stopAr() {
        // Stop GL even if reference preparation has not finished yet.
        camera.onPause(); camera.arSession=null
        if(running) {
            runCatching { session?.pause() }
            running=false
        }
        overlay.cards=emptyList();overlay.points=null; overlay.genericPoints=null; overlay.invalidate()
    }
    fun showPreloadError(message: String) { batchProgress.visibility=View.GONE; status.visibility=View.VISIBLE; status.text=message }
    private fun fail(message: String) { batchProgress.visibility=View.GONE; status.visibility=View.VISIBLE; status.text=message; retry.visibility=View.VISIBLE }
    override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { resumed=false; stopAr() }
    override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { close() }
    fun close() {
        if(destroyed) return
        destroyed=true; resumed=false;imageCache.close()
        host.lifecycle.removeObserver(this); priceObserverCleanup?.invoke(); priceObserverCleanup=null
        camera.releaseEffects()
        stopAr(); camera.closeOutline(); effectLoader.shutdownNow()
        val old=session; session=null
        // Finish any pending reference build before closing its native session.
        worker.execute { camera.awaitReleasedImages(); referenceCache.clear(); old?.close() }; worker.shutdown()
    }
}
