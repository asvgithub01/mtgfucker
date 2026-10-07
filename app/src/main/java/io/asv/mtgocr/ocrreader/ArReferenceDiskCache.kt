package io.asv.mtgocr.ocrreader

import java.io.File
import java.security.MessageDigest

/** Bounded, disposable compiled references. Only serialized bytes cross AR Sessions. */
class ArReferenceDiskCache(private val directory: File) {
    fun file(printing: String,image: String): File {
        val bytes="v1|width=0.063|512x716|$printing|$image".toByteArray(Charsets.UTF_8)
        val key=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return File(directory,"$key.imgdb")
    }
    fun read(printing: String,image: String): ByteArray? {
        val f=file(printing,image)
        if(!f.isFile || f.length()>8L*1024*1024) return null
        return runCatching { f.readBytes().also { f.setLastModified(System.currentTimeMillis()) } }.getOrNull()
    }
    fun discard(printing: String,image: String) { file(printing,image).delete() }
    fun write(printing: String,image: String,bytes: ByteArray) {
        if(bytes.size>8*1024*1024) return
        directory.mkdirs()
        val target=file(printing,image)
        val temporary=File(directory,target.name+".tmp")
        try {
            temporary.writeBytes(bytes)
            if(!temporary.renameTo(target)) return
            var total=0L
            directory.listFiles { f -> f.name.endsWith(".imgdb") }?.sortedByDescending { it.lastModified() }
                ?.forEachIndexed { index,f ->
                    total+=f.length()
                    if(index>=32 || total>32L*1024*1024) f.delete()
                }
        } finally { temporary.delete() }
    }
}
