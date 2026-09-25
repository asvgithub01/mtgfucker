package io.asv.mtgocr.ocrreader

/** Isolated rules experiment. Camera, crop, session, copies and persistence remain shared. */
class RulesScanActivity : RapidEditionScanActivity() {
    override val hashOnlyMode = true
    override val rulesScannerMode = true
}
