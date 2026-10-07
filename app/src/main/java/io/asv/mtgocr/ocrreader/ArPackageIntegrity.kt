package io.asv.mtgocr.ocrreader
import java.security.MessageDigest
object ArPackageIntegrity {
    fun safeFile(name:String)=name.matches(Regex("[a-z0-9-]+\\.(imgdb|json)"))
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun verify(bytes:ByteArray,size:Long,sha:String) {
        require(size in 1..16L*1024*1024 && sha.matches(Regex("[0-9a-f]{64}")))
        require(bytes.size.toLong()==size && hash(bytes)==sha)
    }
}
