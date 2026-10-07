package io.asv.mtgocr.ocrreader

import io.asv.collectorvision.EdScanPerf
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.WindowManager
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Camera texture + ARCore pose projection. No CameraX and no fake screen-space anchor. */
class TripleArSurface(context: Context,
    private val onPoints: (FloatArray?)->Unit,
    private val onTracking: (Boolean)->Unit,
    private val onError: (String)->Unit,
    private val wantsFrame: ()->Boolean = { false },
    private val onCpuFrame: (android.media.Image,Int,()->Unit,String?,FloatArray?,FloatArray?)->Boolean = { _,_,_,_,_,_-> false },
    private val onGenericPoints: (FloatArray?)->Unit = {},
    private val onPreloadedCard: (ArPreloadCard)->Unit = {}
) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile var multipleCardsEnabled=false
    @Volatile var primaryPrice=""
    var onVisibleCards:(List<TripleArOverlay.Card>)->Unit={}
    private var lastVisibleCount=-1
    @Volatile var arSession: Session?=null
    @Volatile var targetName=""
    @Volatile var preloadMode=false
    @Volatile var rarityColors=emptyMap<String,Int>()
    @Volatile var targetColor=TripleRarityColor.color(null)
    @Volatile var titleGuideEnabled=true
    @Volatile var preferredTargetName=""
    @Volatile var preloadedCards: Map<String,ArPreloadCard> = emptyMap()
    private var lastPreloadedName=""
    @Volatile var sensorRotation=90
    @Volatile var genericOutlineEnabled=false
    @Volatile var revealEnabled=false
    @Volatile var confettiEnabled=false
    private var confetti: TripleConfetti?=null
    private var confettiName=""
    private var confettiStarted=0L
    @Volatile private var genericOutline: ArGenericOutline?=null
    private val outlineWorker=java.util.concurrent.Executors.newSingleThreadExecutor()
    private val outlineBusy=java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastOutlineAt=0L
    @Volatile private var outlineEpoch=0
    private val contourDetector=OpenCvCardDetector()
    private var contourReady=false
    @Volatile private var genericProcessedFrames=0
    @Volatile var energyBorderEnabled=false
    private var energyBorder: TripleEnergyBorder?=null
    private var reveal: ArCardReveal?=null
    private var revealBitmap: android.graphics.Bitmap?=null
    private var revealPrinting=""
    private var revealStarted=0L
    private var revealWasEnabled=false
    private var revealFailed=false
    fun setRevealImage(printing: String,bitmap: android.graphics.Bitmap) {
        queueEvent {
            revealBitmap?.recycle(); revealBitmap=bitmap; revealPrinting=printing
            reveal?.close(); reveal=null; revealFailed=false; revealStarted=0L
        }
    }
    fun releaseEffects() {
        queueEvent {
            revealBitmap?.recycle(); revealBitmap=null
            reveal?.close(); reveal=null; energyBorder?.close(); energyBorder=null; confetti?.close(); confetti=null
        }
    }
    private var energyBorderFailed=false
    private val effectPose=FloatArray(16)
    private var lastCpuTimestamp=0L
    private var lastDrawAt=0L
    private val leases=java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var cpuFrames=0; private set
    fun awaitReleasedImages() { while(leases.get()>0) Thread.sleep(5) }
    private var texture=0
    private var program=0
    private var viewWidth=1
    private var viewHeight=1
    private var tracking: Boolean?=null
    private var failed=false
    private val vertices=buffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val uv=buffer(FloatArray(8))
    private val projection=FloatArray(16)
    private val view=FloatArray(16)
    private val vp=FloatArray(16)
    init { setEGLContextClientVersion(2); preserveEGLContextOnPause=true; setRenderer(this) }
    private fun buffer(data: FloatArray): FloatBuffer=ByteBuffer.allocateDirect(data.size*4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        confetti=null; confettiName=""; energyBorder=null; energyBorderFailed=false; reveal=null; revealFailed=false; revealStarted=0L
        try {
        val ids=IntArray(1); GLES20.glGenTextures(1,ids,0); texture=ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main(){gl_Position=vec4(aPosition,0.0,1.0);vUv=aUv;}")
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uCamera; varying vec2 vUv; void main(){gl_FragColor=texture2D(uCamera,vUv);}")
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        val linked=IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0)
        check(linked[0]!=0) { GLES20.glGetProgramInfoLog(program) }
        failed=false; tracking=null
        } catch(e: Exception) { program=0; onError(e.message.orEmpty()) }
    }
    fun resetTracking() { queueEvent { failed=false; tracking=null; outlineEpoch++; genericOutline=null; onGenericPoints(null) } }
    fun closeOutline() { outlineWorker.shutdownNow() }
    private fun submitOutline(image: android.media.Image,frame: Frame,rotation: Int,capturedAt: Long) {
        if(!outlineBusy.compareAndSet(false,true)) return
        lastOutlineAt=capturedAt
        try {
            val scale=minOf(1f,320f/maxOf(image.width,image.height))
            val w=(image.width*scale).toInt().coerceAtLeast(1)
            val h=(image.height*scale).toInt().coerceAtLeast(1)
            val plane=image.planes[0]
            val buffer=plane.buffer.duplicate()
            val bytes=ByteArray(buffer.remaining()).also { buffer.get(it) }
            val luma=ByteArray(w*h)
            for(y in 0 until h) for(x in 0 until w)
                luma[y*w+x]=bytes[(y*image.height/h)*plane.rowStride+(x*image.width/w)*plane.pixelStride]
            val basis=FloatArray(6)
            frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED,floatArrayOf(0f,0f,1f,0f,0f,1f),Coordinates2d.VIEW,basis)
            val epoch=outlineEpoch; val vw=viewWidth; val vh=viewHeight
            outlineWorker.execute {
                try {
                    if(!contourReady) contourReady=org.opencv.android.OpenCVLoader.initLocal()
                    val quad=if(contourReady) EdScanPerf.measure("AR.genericContour") { contourDetector.detectLuma(luma,w,h) } else null
                    genericProcessedFrames++
                    val points=quad?.normalizedCorners?.flatMap { ArGenericOutline.project(it.x,it.y,basis).toList() }?.toFloatArray()
                    if(epoch==outlineEpoch) genericOutline=points?.let { ArGenericOutline(it,capturedAt,vw,vh,rotation) }
                } catch(e: Exception) {
                    genericOutline=null
                    android.util.Log.w("EdScanPerf","Generic contour skipped",e)
                } finally { outlineBusy.set(false) }
            }
        } catch(e: Exception) {
            outlineBusy.set(false)
            android.util.Log.w("EdScanPerf","Generic frame skipped",e)
        }
    }
    private fun shader(type: Int,source: String): Int {
        val shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader)
        val result=IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,result,0)
        check(result[0]!=0) { GLES20.glGetShaderInfoLog(shader) }; return shader
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth=width; viewHeight=height; GLES20.glViewport(0,0,width,height)
    }
    @Volatile var recognitionEnabled=true
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val ar=arSession ?: return
        if(program==0 || failed) return
        val drawStarted=System.nanoTime()
        if(lastDrawAt!=0L) EdScanPerf.record("AR.drawInterval",drawStarted-lastDrawAt)
        lastDrawAt=drawStarted
        try {
            @Suppress("DEPRECATION")
            val rotation=(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            ar.setDisplayGeometry(rotation,viewWidth,viewHeight)
            ar.setCameraTextureName(texture)
            val frame=EdScanPerf.measure("AR.sessionUpdate") { ar.update() }
            if(frame.timestamp==0L) { publish(null); return }
            val trackedImages=if(recognitionEnabled && frame.camera.trackingState==TrackingState.TRACKING) ar.getAllTrackables(AugmentedImage::class.java).sortedByDescending { it.name==preferredTargetName }.filter {
                (if(preloadMode) it.name in preloadedCards else it.name==targetName) && it.trackingState==TrackingState.TRACKING && it.trackingMethod==AugmentedImage.TrackingMethod.FULL_TRACKING
            } else emptyList()
            val tracked=trackedImages.firstOrNull()
            val imageCorners=tracked?.let { target ->
                frame.camera.getProjectionMatrix(projection,0,.01f,10f)
                frame.camera.getViewMatrix(view,0);Matrix.multiplyMM(vp,0,projection,0,view,0)
                val x=target.extentX/2;val z=target.extentZ/2
                val screen=arrayOf(floatArrayOf(-x,0f,-z),floatArrayOf(x,0f,-z),floatArrayOf(x,0f,z),floatArrayOf(-x,0f,z)).map { local ->
                    ArCardProjection.project(vp,target.centerPose.transformPoint(local),viewWidth,viewHeight)
                }
                if(screen.any { it==null }) null else FloatArray(8).also { pixels ->
                    frame.transformCoordinates2d(Coordinates2d.VIEW,screen.flatMap { it!!.toList() }.toFloatArray(),Coordinates2d.IMAGE_PIXELS,pixels)
                }
            }
            val needRecognition=wantsFrame()
            val now=android.os.SystemClock.uptimeMillis()
            val needOutline=recognitionEnabled && genericOutlineEnabled && !preloadMode && now-lastOutlineAt>=125 && !outlineBusy.get()
            if(frame.timestamp!=lastCpuTimestamp && (needRecognition || needOutline)) {
                try {
                    val image=EdScanPerf.measure("AR.acquireImage") { frame.acquireCameraImage() }
                    EdScanPerf.record("AR.frameAvailable",0)
                    lastCpuTimestamp=frame.timestamp; leases.incrementAndGet()
                    val released=java.util.concurrent.atomic.AtomicBoolean(false)
                    val release={ if(released.compareAndSet(false,true)) { try { image.close() } finally { leases.decrementAndGet() } }; Unit }
                    try {
                        val degrees=(sensorRotation-rotation*90+360)%360
                        if(needOutline) submitOutline(image,frame,rotation,now)
                        if(needRecognition) {
                            cpuFrames++
                            val guide=if(titleGuideEnabled) FloatArray(8).also {
                                frame.transformCoordinates2d(Coordinates2d.VIEW,TripleTitleGuide.points(viewWidth,viewHeight),Coordinates2d.IMAGE_PIXELS,it)
                            } else null
                            if(!onCpuFrame(image,degrees,release,tracked?.name,imageCorners,guide)) release()
                        } else release()
                    } catch(e: Exception) { release(); throw e }
                } catch(_: com.google.ar.core.exceptions.NotYetAvailableException) { EdScanPerf.record("AR.imageNotAvailable",0) }
            } else EdScanPerf.record("AR.frameSkippedBusyOrDuplicate",0)
            vertices.position(0); uv.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uCamera"),0)
            val position=GLES20.glGetAttribLocation(program,"aPosition"); val tex=GLES20.glGetAttribLocation(program,"aUv")
            vertices.position(0); uv.position(0)
            GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(tex)
            GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,vertices)
            GLES20.glVertexAttribPointer(tex,2,GLES20.GL_FLOAT,false,0,uv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
            GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(tex)
            val outline=genericOutline
            onGenericPoints(if(recognitionEnabled && genericOutlineEnabled && !preloadMode && outline?.valid(now,viewWidth,viewHeight,rotation)==true) outline.points else null)
            val visibleImages=TripleArRenderPolicy.visible(trackedImages,multipleCardsEnabled)
            val image=visibleImages.firstOrNull()
            if(image==null) { revealStarted=0L; lastPreloadedName=""; publish(null); return }
            if(preloadMode) {
                val card=preloadedCards[image.name] ?: return
                targetName=image.name
                if(lastPreloadedName!=image.name) {
                    lastPreloadedName=image.name
                    EdScanPerf.event("AR.preloadedDetected")
                    onPreloadedCard(card)
                }
            }
            frame.camera.getProjectionMatrix(projection,0,0.01f,10f)
            frame.camera.getViewMatrix(view,0); Matrix.multiplyMM(vp,0,projection,0,view,0)
            val x=image.extentX/2; val z=image.extentZ/2
            val points=ArrayList<Float>()
            for(local in arrayOf(floatArrayOf(-x,0f,-z),floatArrayOf(x,0f,-z),floatArrayOf(x,0f,z),floatArrayOf(-x,0f,z))) {
                val world=image.centerPose.transformPoint(local)
                val screen=ArCardProjection.project(vp,world,viewWidth,viewHeight)
                if(screen==null) { publish(null); return }
                points.add(screen[0]); points.add(screen[1])
            }
            if((energyBorderEnabled || revealEnabled) && !energyBorderFailed) {
                try {
                    val effect=energyBorder ?: TripleEnergyBorder().also { energyBorder=it }
                    image.centerPose.toMatrix(effectPose,0)
                    EdScanPerf.measure("AR.energyBorder") { effect.draw(vp,effectPose,image.extentX,image.extentZ,rarityColors[image.name] ?: if(preloadMode) TripleRarityColor.color(preloadedCards[image.name]?.rarity) else targetColor) }
                } catch(e: Exception) {
                    energyBorderFailed=true
                    android.util.Log.w("EdScanPerf","Energy shader disabled; camera stays active",e)
                }
            }
            if(!revealEnabled) { revealStarted=0L; revealWasEnabled=false }
            if(revealEnabled && !revealFailed && revealPrinting==targetName && revealBitmap!=null) {
                try {
                    val effect=reveal ?: ArCardReveal().also { it.upload(requireNotNull(revealBitmap)); reveal=it }
                    if(revealStarted==0L || !revealWasEnabled) revealStarted=android.os.SystemClock.uptimeMillis()
                    revealWasEnabled=true
                    image.centerPose.toMatrix(effectPose,0)
                    EdScanPerf.measure("AR.reveal") {
                        effect.draw(vp,effectPose,image.extentX,image.extentZ,(android.os.SystemClock.uptimeMillis()-revealStarted)/1000f)
                    }
                } catch(e: Exception) { revealFailed=true; android.util.Log.w("EdScanPerf","Reveal disabled",e) }
            }
            if(confettiEnabled) {
                try {
                    if(confettiName!=image.name) { confettiName=image.name;confettiStarted=now }
                    val elapsed=(now-confettiStarted)/1000f
                    if(elapsed in .4f..3.4f) {
                        image.centerPose.toMatrix(effectPose,0)
                        val effect=confetti ?: TripleConfetti().also { confetti=it }
                        EdScanPerf.measure("triple.confettiDraw") { effect.draw(vp,effectPose,elapsed-.4f) }
                    }
                } catch(error:Exception) { confettiEnabled=false;android.util.Log.w("EdScanPerf","Triple confetti disabled",error) }
            } else confettiName=""
            val overlays=mutableListOf<TripleArOverlay.Card>()
            fun overlayCard(target:AugmentedImage,quad:FloatArray) {
                val card=preloadedCards[target.name]
                overlays.add(TripleArOverlay.Card(target.name,quad,card?.priceText ?: primaryPrice,rarityColors[target.name] ?: if(preloadMode) TripleRarityColor.color(card?.rarity) else targetColor))
            }
            overlayCard(image,points.toFloatArray())
            visibleImages.drop(1).forEach { extra ->
                val ex=extra.extentX/2;val ez=extra.extentZ/2
                val quad=arrayOf(floatArrayOf(-ex,0f,-ez),floatArrayOf(ex,0f,-ez),floatArrayOf(ex,0f,ez),floatArrayOf(-ex,0f,ez)).map {
                    ArCardProjection.project(vp,extra.centerPose.transformPoint(it),viewWidth,viewHeight)
                }
                if(quad.all { it!=null }) {
                    overlayCard(extra,quad.flatMap { it!!.toList() }.toFloatArray())
                    if((energyBorderEnabled || revealEnabled) && !energyBorderFailed) try {
                        val effect=energyBorder ?: TripleEnergyBorder().also { energyBorder=it }
                        extra.centerPose.toMatrix(effectPose,0)
                        EdScanPerf.measure("triple.multiBorder") { effect.draw(vp,effectPose,extra.extentX,extra.extentZ,overlays.last().color) }
                    } catch(_:Exception) { energyBorderFailed=true }
                }
            }
            if(lastVisibleCount!=overlays.size) { lastVisibleCount=overlays.size;EdScanPerf.event("triple.visibleCards count=${overlays.size} multiple=$multipleCardsEnabled") }
            publish(points.toFloatArray())
            onVisibleCards(overlays)
        } catch(e: Exception) {
            publish(null)
            if(!failed) { failed=true; onError(e.message ?: e.javaClass.simpleName) }
        } finally { EdScanPerf.record("AR.drawWork",System.nanoTime()-drawStarted) }
    }
    private fun publish(points: FloatArray?) {
        if(points==null) { onVisibleCards(emptyList());lastVisibleCount=0 }
        onPoints(points)
        val now=points!=null
        if(tracking!=now) { tracking=now; EdScanPerf.event("AR.tracking=$now"); onTracking(now) }
    }
}
