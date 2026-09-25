package io.asv.mtgocr.ocrreader

import android.Manifest
import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Binds the same three CameraX use cases without opening a scanner or touching the collection. */
class SymbolCameraResolutionDeviceTest {

    @Test fun stableOcrCameraConfigurationProducesFramesWithPreviewAndStillCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        val provider = ProcessCameraProvider.getInstance(context).get(20, TimeUnit.SECONDS)
        val executor = Executors.newSingleThreadExecutor()
        val received = CountDownLatch(1)
        var actual: Size? = null
        var owner: LifecycleOwner? = null
        try {
            instrumentation.runOnMainSync {
                val lifecycleOwner = object : LifecycleOwner {
                    val registry = LifecycleRegistry(this)
                    override val lifecycle: Lifecycle get() = registry
                }
                owner = lifecycleOwner
                lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
                val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                preview.setSurfaceProvider { request ->
                    val texture = SurfaceTexture(0)
                    texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    val surface = Surface(texture)
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                        surface.release(); texture.release()
                    }
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(SymbolResolutionPolicy.CAMERA_WIDTH, SymbolResolutionPolicy.CAMERA_HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    if (actual == null) { actual = Size(frame.width, frame.height); received.countDown() }
                    frame.close()
                }
                val still = ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, still)
            }
            assertTrue("No camera frame received", received.await(20, TimeUnit.SECONDS))
            val size = checkNotNull(actual)
            Log.i("SymbolCameraResolution", "requested=1280x960 actual=$size")
            assertTrue("Camera returned $size", size.width > 0 && size.height > 0)
            assertEquals(1280, SymbolResolutionPolicy.CAMERA_WIDTH)
            assertEquals(960, SymbolResolutionPolicy.CAMERA_HEIGHT)
        } finally {
            instrumentation.runOnMainSync {
                owner?.let { provider.unbindAll(); (it.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.DESTROYED }
            }
            executor.shutdown()
        }
    }
}
