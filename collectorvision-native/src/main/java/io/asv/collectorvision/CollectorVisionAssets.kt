/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.asv.collectorvision

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Pinned upstream stable release. Never silently mix a new model with an old catalog. */
object CollectorVisionAssets {
    private const val BASE_VERSION = "milo1-scryfall-mtg-2026-07-09-cornelius2.12"
    const val VERSION = "$BASE_VERSION-fra-frc-spg20260928"
    const val FRA_EMBEDDINGS = "fra-20260928-embeddings.f16.bin"
    const val FRA_IDS = "fra-20260928-card-ids.json"
    const val FRA_ROWS = 454
    const val COLLECTOR_EMBEDDINGS = "collector-20260928-embeddings.f16.bin"
    const val COLLECTOR_IDS = "collector-20260928-card-ids.json"
    const val COLLECTOR_ROWS = 113
    const val TOTAL_BYTES = 42092791L
    data class Asset(val path: String, val bytes: Long, val sha256: String) {
        val fileName: String get() = path.substringAfterLast('/')
    }
    val assets = listOf(
        Asset("models/detector.onnx",4407545,"650da3cc3e9ac778c6951de631f824ec1e63bdabf3aaa39a35d7435af625612e"),
        Asset("models/milo.onnx",5191100,"bd13d8d60383c69da04dce261f32e93fdaeaa8fd618fbc991e7385f71b3d45df"),
        Asset("catalog/scryfall-mtg-embeddings.f16.bin",28086016,"50db45bb77ca753357b48ae715edf3209016a0f28ab2f4506f302258c4fde800"),
        Asset("catalog/scryfall-mtg-card-ids.json",4408130,"48b26f73e6f65d0591e18ea0b335091eed28d219f26ae097428f9a3dc77d0f22")
    )
    val fraAssets = listOf(
        Asset("edscan-fra/$FRA_EMBEDDINGS",116224,"88cce743f5a24ade240ce2b60cd18fd558a282213a66274f1cd8cc99a921e62b"),
        Asset("edscan-fra/$FRA_IDS",17707,"9474bf61df3ce413dbd86e51ce43db1cec69983c5908bba8bd1f37b4244f485d"),
        Asset("edscan-fra/$COLLECTOR_EMBEDDINGS",28928,"006244b0a6dd51cffd7d73c141bac7140978d471bac047cf43e41ec8454e913b"),
        Asset("edscan-fra/$COLLECTOR_IDS",4408,"3281d65d4bf545c5ff323d432e40cf7de5c10d38c60e4407a633c354d4feeaee")
    )
    // Reuse the verified base models/catalog instead of downloading them again for a small supplement.
    fun directory(context: Context) = File(context.noBackupFilesDir, "collectorvision-native/$BASE_VERSION")
    @Synchronized
    fun prepare(context: Context, onProgress: (String) -> Unit): File {
        val dir = directory(context).apply { check(isDirectory || mkdirs()) }
        for (asset in assets) {
            checkInterrupted()
            val destination = File(dir, asset.fileName)
            onProgress("${asset.fileName} · SHA-256")
            if (valid(destination, asset)) continue
            val temporary = File(dir, asset.fileName + ".part")
            val connection = URL("https://hanclinto.github.io/CollectorVision/assets/${asset.path}").openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            try {
                check(connection.responseCode == 200) { "HTTP ${connection.responseCode}: ${asset.fileName}" }
                connection.inputStream.use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    var lastReport = 0L
                    while (true) {
                        checkInterrupted()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        check(total <= asset.bytes) { "Unexpected asset size: ${asset.fileName}" }
                        output.write(buffer,0,n)
                        if (total - lastReport >= 1048576 || total == asset.bytes) {
                            onProgress("${asset.fileName} · ${total * 100 / asset.bytes}%")
                            lastReport = total
                        }
                    }
                } }
                check(valid(temporary, asset)) { "SHA-256 mismatch: ${asset.fileName}" }
                check(!destination.exists() || destination.delete())
                check(temporary.renameTo(destination)) { "Cannot publish validated asset" }
            } finally {
                connection.disconnect()
                temporary.delete()
            }
        }
        for(asset in fraAssets) {
            checkInterrupted()
            val destination=File(dir,asset.fileName)
            if(valid(destination,asset)) continue
            val temporary=File(dir,asset.fileName+".part")
            try {
                context.assets.open(asset.path).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(valid(temporary,asset)) { "Invalid FRA supplement: ${asset.fileName}" }
                check(!destination.exists() || destination.delete())
                check(temporary.renameTo(destination))
            } finally { temporary.delete() }
        }
        return dir
    }
    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Asset preparation cancelled")
    }
    internal fun valid(file: File, asset: Asset): Boolean {
        if (!file.isFile || file.length() != asset.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                checkInterrupted()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer,0,n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256
    }
}
