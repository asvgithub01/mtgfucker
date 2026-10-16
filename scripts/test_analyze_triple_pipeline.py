import unittest
from analyze_triple_pipeline import summarize


class PipelineTest(unittest.TestCase):
    def test_window_and_expired_lanes_include_stale_results_without_summing_overlapping_work(self):
        result = summarize([
            "99 triple.ocrLane totalMs=9000 expired=true",
            "100 triple.methods ar=true art=true ocr=true",
            "101 triple.ocrLane totalMs=2600 modelsMs=2100 namesMs=600 languageMs=-1 expired=true captureAgeMs=2700",
            "102 triple.titleResolved ms=600 captureAgeMs=2700",
            "103 triple.ocrLane totalMs=800 modelsMs=700 namesMs=30 languageMs=20 expired=false captureAgeMs=900",
            "104 triple.titleResolved ms=30 captureAgeMs=900",
            "105 triple.methodSample method=ocr model=latin ms=200 success=true",
            "106 triple.health thermal=0 heapMb=220 traceDropped=2",
            "107 triple.health thermal=0 heapMb=250 traceDropped=3",
            "108 triple.lookup source=local ms=50",
            "109 triple.confirmation reason=margin frameMs=850 searchMs=70",
            "110 triple.lane lane=OCR queueAndDecodeMs=100",
            "111 triple.languageScope language=en",
            "112 triple.ocrLane totalMs=9000 expired=true",
        ], 100, 111)
        self.assertEqual(2, result["ocrLaneReleases"])
        self.assertEqual(1, result["ocrLaneExpired"])
        self.assertEqual(1, result["titleCallbacksOlderThan2500ms"])
        self.assertEqual(1, result["stagesMs"]["triple.ocrLane.languageMs"]["n"])
        self.assertEqual(2600, result["stagesMs"]["triple.ocrLane.totalMs"]["max"])
        self.assertEqual(200, result["ocrModelsMs"]["latin"]["p50"])
        self.assertEqual(3, result["traceDroppedMax"])
        self.assertEqual({"margin": 1}, result["confirmationReasons"])
        self.assertEqual({"local": 1}, result["lookupSources"])
        self.assertEqual(1, len(result["methodChanges"]))
        self.assertEqual(1, len(result["languageScopeChanges"]))

    def test_legacy_missing_malformed_and_empty_values_are_not_zero_duration_samples(self):
        r = summarize(["not a trace", "1 triple.confirmation reason=no_detection",
                       "2 triple.ocrLane totalMs=oops modelsMs=-1 namesMs=nan languageMs=inf",
                       "3 triple.ocrComplete elapsedMs=900", "4 triple.save ms=0"])
        self.assertNotIn("triple.confirmation.frameMs", r["stagesMs"])
        self.assertNotIn("triple.ocrLane.modelsMs", r["stagesMs"])
        self.assertEqual(0, r["stagesMs"]["triple.save.ms"]["p50"])
        self.assertIsNone(summarize([])["firstAt"])
        self.assertIsNone(summarize([])["heapMb"]["p95"])


if __name__ == "__main__":
    unittest.main()
