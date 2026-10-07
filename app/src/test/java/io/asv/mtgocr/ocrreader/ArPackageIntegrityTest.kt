package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class ArPackageIntegrityTest {
    @Test fun rejectsPathsAndUnversionedOddNames() {
        listOf("../x.imgdb","/x.imgdb","https://x/a.imgdb","a\\b.imgdb","a.imgdb?x").forEach { assertFalse(ArPackageIntegrity.safeFile(it)) }
        assertTrue(ArPackageIntegrity.safeFile("fra-abc123.imgdb"))
    }
    @Test fun acceptsExactContent() { val b="test".toByteArray();ArPackageIntegrity.verify(b,4,ArPackageIntegrity.hash(b)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsCorruption() { ArPackageIntegrity.verify("changed".toByteArray(),7,ArPackageIntegrity.hash("original".toByteArray())) }
    @Test(expected=IllegalArgumentException::class) fun rejectsTruncation() { val b="a".toByteArray();ArPackageIntegrity.verify(b,2,ArPackageIntegrity.hash(b)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsOversizedDeclaration() { ArPackageIntegrity.verify(byteArrayOf(),17L*1024*1024,"0".repeat(64)) }
}
