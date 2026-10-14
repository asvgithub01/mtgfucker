package io.asv.mtgocr.ocrreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Opt-in live camera profiling. Uses the real pipeline but its persistence is disabled. */
@RunWith(AndroidJUnit4::class)
class TripleCameraLoadTest {
    @Test fun profileLiveCameraWithoutSavingCards() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTriple")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val files=LibraryCatalog.libraries(context).map { File(context.filesDir,it.fileName) }.filter { it.isFile }
        fun hashes()=files.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        val before=hashes()
        val intent=android.content.Intent(context,TripleScanActivity::class.java).putExtra("triple_diagnostic_only",true)
        try { androidx.test.core.app.ActivityScenario.launch<TripleScanActivity>(intent).use {
            val args=InstrumentationRegistry.getArguments()
            Thread.sleep(args.getString("durationMs")?.toLong()?.coerceIn(30000,180000) ?: 30000)
            args.getString("expectedPackageTarget")?.let { target ->
                it.onActivity { activity ->
                    check(TripleScanActivity::class.java.getDeclaredField("activePackage").apply { isAccessible=true }.getBoolean(activity))
                    val references=TripleScanActivity::class.java.getDeclaredField("references").apply { isAccessible=true }.get(activity) as Map<*,*>
                    check(references.values.any { value -> value!!.javaClass.getDeclaredField("id").apply { isAccessible=true }.get(value)==target })
                    val preview=TripleScanActivity::class.java.getDeclaredField("preview").apply { isAccessible=true }.get(activity)
                    check(TripleArPreview::class.java.getDeclaredField("batchReadyCount").apply { isAccessible=true }.getInt(preview) in 574..1000) { "Compiled database must actually be installed in the AR session" }
                    check(references.size>=564)
                    check(references.values.none { value -> value!!.javaClass.getDeclaredField("set").apply { isAccessible=true }.get(value)=="SPG" }) { "FRA + FRC must exclude SPG from accepted package references" }
                }
            }
        }
        } finally { check(before==hashes()) { "Diagnostic camera must never write inventory" } }
    }
}
