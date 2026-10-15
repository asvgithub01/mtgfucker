package io.asv.mtgocr.ocrreader
import org.junit.Assert.*
import org.junit.Test
class CloudBuylistPolicyTest {
    private val p=CloudBuylistPolicy
    @Test fun firstUploadAndFreshInstallRestore() {
        assertEquals(CloudBuylistPolicy.Action.UPLOAD,p.action("local",null,null))
        assertEquals(CloudBuylistPolicy.Action.RESTORE,p.action(null,"remote",null))
        assertEquals(CloudBuylistPolicy.Action.SAME,p.action(null,null,null))
    }
    @Test fun identicalContentsAreNoOpEvenWithUnknownBase() {
        assertEquals(CloudBuylistPolicy.Action.SAME,p.action("same","same",null))
    }
    @Test fun trackedEmptyDeletionUploadsInsteadOfResurrectingRemote() {
        assertEquals(CloudBuylistPolicy.Action.UPLOAD,p.action("empty","old","old"))
    }
    @Test fun unchangedLocalAcceptsRemoteUpdateOrDeletion() {
        assertEquals(CloudBuylistPolicy.Action.RESTORE,p.action("old","empty","old"))
    }
    @Test fun concurrentEditsAndUntrackedDivergenceNeverOverwrite() {
        assertEquals(CloudBuylistPolicy.Action.CONFLICT,p.action("local","remote","base"))
        assertEquals(CloudBuylistPolicy.Action.CONFLICT,p.action("local","remote",null))
    }
    @Test fun webAndAndroidEncodingDifferencesAreNotLocalEdits() {
        assertEquals(CloudBuylistPolicy.Action.SAME,p.action("android","web","web","android"))
        assertEquals(CloudBuylistPolicy.Action.RESTORE,p.action("android","web-edit","web","android"))
        assertEquals(CloudBuylistPolicy.Action.UPLOAD,p.action("android-edit","web","web","android"))
        assertEquals(CloudBuylistPolicy.Action.CONFLICT,p.action("android-edit","web-edit","web","android"))
    }
}
