import unittest
import numpy as np
from PIL import Image
from evaluate_edfast_regions import region, rectify, reference_profile, lane_summary, shadow_choice


class RegionTests(unittest.TestCase):
    def test_upper_omits_description_and_art_omits_title(self):
        pixels = np.zeros((100, 100, 3), dtype=np.uint8)
        pixels[:15] = [255, 0, 0]  # title
        pixels[15:55] = [0, 255, 0]  # illustration
        pixels[58:] = [0, 0, 255]  # rules
        image = Image.fromarray(pixels)
        self.assertEqual((100, 58), region(image, "upper").size)
        self.assertFalse((np.asarray(region(image, "upper"))[:, :, 2] > 0).any())
        self.assertTrue((np.asarray(region(image, "art")) == [0, 255, 0]).all())
        self.assertEqual(image.tobytes(), region(image, "full").tobytes())

    def test_rectification_requires_real_normalized_corners(self):
        image = Image.new("RGB", (100, 100))
        for points in [[], [[0, 0]]*3, [[-1, 0]]*4, [[float("nan"), 0]]*4]:
            with self.assertRaises(ValueError):
                rectify(image, points)
        self.assertEqual((448, 448), rectify(image, [[0, 0], [1, 0], [1, 1], [0, 1]]).size)

    def test_hybrid_routes_full_art_and_special_layouts_to_whole_card(self):
        normal = {"full_art": False, "layout": "normal", "border_color": "black"}
        self.assertEqual("art", reference_profile(normal))
        for ref in [{}, {**normal, "full_art": True}, {**normal, "layout": "adventure"},
                    {**normal, "layout": "transform"}, {**normal, "layout": "split"},
                    {**normal, "frame_effects": ["showcase"]}, {**normal, "border_color": "borderless"}]:
            self.assertEqual("full", reference_profile(ref))

    def test_margins_use_distinct_identities_not_duplicate_printings(self):
        refs = [{"id": "a1", "identity": "a"}, {"id": "a2", "identity": "a"}, {"id": "b", "identity": "b"}]
        r = lane_summary(refs, np.array([.9, .89, .6]))
        self.assertAlmostEqual(.3, r["margin"])
        self.assertTrue(r["strongHypothesis"])
        self.assertFalse(lane_summary(refs[:2], np.array([.9, .89]))["strongHypothesis"])

    def test_lane_conflict_never_resolved_by_larger_uncalibrated_score(self):
        a = {"identity": "a", "score": .99, "strongHypothesis": True}
        b = {"identity": "b", "score": .75, "strongHypothesis": True}
        self.assertEqual({"status": "conflict", "candidate": None}, shadow_choice({"art": a, "full": b}))
        self.assertEqual("agreement", shadow_choice({"art": a, "full": {**a, "score": .75}})["status"])
        self.assertEqual("single_lane_candidate", shadow_choice({"art": a, "full": None})["status"])
        self.assertEqual("weak", shadow_choice({"art": None, "full": None})["status"])


if __name__ == "__main__":
    unittest.main()
