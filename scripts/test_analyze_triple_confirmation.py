import unittest
from analyze_triple_confirmation import summarize


class SummaryTest(unittest.TestCase):
    def test_counts_transitions_and_reports_slow_causes_without_claiming_accuracy(self):
        result = summarize([
            "1 triple.confirmation reason=margin frameMs=600 detectMs=100 featureMs=490 searchMs=10",
            "2 triple.confirmation target=123 reason=confirmed strongReadings=2 confirmMs=600 frameMs=600",
            "3 triple.confirmation reason=confirmed strongReadings=3 confirmMs=1200 frameMs=600",
            "4 triple.acceptInterval deltaMs=-1",
            "5 triple.acceptInterval deltaMs=599",
            "6 triple.acceptInterval deltaMs=1200",
        ])
        self.assertEqual(1, result["confirmationCount"])
        self.assertEqual(1200, result["confirmationMs"]["p95"])
        self.assertEqual("123", result["slowConfirmations"][0]["target"])
        self.assertEqual(1, result["newCopyIntervalsBelow600ms"])
        self.assertEqual(10, result["stagesMs"]["searchMs"]["p50"])

    def test_empty_trace_does_not_claim_fast_confirmation(self):
        self.assertIsNone(summarize([])["confirmationMs"]["p95"])

    def test_never_confirmed_candidates_keep_timestamp_and_rejection_reason(self):
        result = summarize([
            "10 triple.confirmation target=123 reason=margin confirmMs=1500 frameMs=150",
            "11 triple.confirmation reason=scope_pending atMs=1100",
        ])
        self.assertEqual(0, result["confirmationCount"])
        self.assertEqual("10", result["lastSlowPendingSamples"][0]["timestamp"])
        self.assertEqual("margin", result["lastSlowPendingSamples"][0]["reason"])
        self.assertEqual("11", result["lastBlockedSamples"][0]["timestamp"])


if __name__ == "__main__":
    unittest.main()
