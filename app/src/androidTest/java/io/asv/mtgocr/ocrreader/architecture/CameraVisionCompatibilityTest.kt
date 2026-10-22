package io.asv.mtgocr.ocrreader.architecture

import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import io.asv.collectorvision.CameraVisionAssets
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class CameraVisionCompatibilityTest {
    @Test fun persistedWorkerAndActivityNamesStillResolveWithoutStartingThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf("EdScanPriceWorker", "EdScanLanguageWorker", "EdScanVerificationWorker")) {
            val worker = Class.forName("io.asv.collectorvision.$name", false, context.classLoader)
            assertTrue(ListenableWorker::class.java.isAssignableFrom(worker))
            worker.getConstructor(Context::class.java, androidx.work.WorkerParameters::class.java)
        }
        val component = ComponentName(context.packageName, "io.asv.collectorvision.NativeCollectorVisionActivity")
        val info = context.packageManager.getActivityInfo(component, 0)
        assertFalse(info.exported)
        assertEquals(component.className, info.name)
    }
    @Test fun modelDirectoryAndExistingBytesArePreservedWithoutPreparingOrDownloading() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "camera-compat-" + UUID.randomUUID()).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = root
            override fun getApplicationContext(): Context = this
        }
        try {
            val legacy = File(root, "collectorvision-native/milo1-scryfall-mtg-2026-07-09-cornelius2.12")
            legacy.mkdirs()
            val model = File(legacy, "detector.onnx").apply { writeText("existing fixture, not a real model") }
            val before = model.readBytes()
            assertEquals(legacy.canonicalFile, CameraVisionAssets.directory(context).canonicalFile)
            assertArrayEquals(before, model.readBytes())
            assertEquals(listOf("collectorvision-native"), root.list()!!.toList())
            assertEquals(4, CameraVisionAssets.assets.size)
            assertEquals("650da3cc3e9ac778c6951de631f824ec1e63bdabf3aaa39a35d7435af625612e",
                CameraVisionAssets.assets.first().sha256)
        } finally { root.deleteRecursively() }
    }
}
