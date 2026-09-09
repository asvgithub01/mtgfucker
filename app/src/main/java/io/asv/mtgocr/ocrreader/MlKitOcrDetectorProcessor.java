package io.asv.mtgocr.ocrreader;

import android.graphics.Rect;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;

import io.asv.mtgocr.ocrreader.ui.camera.GraphicOverlay;

import java.util.ArrayList;
import java.util.List;

/** Extracts title candidates from ML Kit while keeping the proven local name-matching flow. */
final class MlKitOcrDetectorProcessor implements Detector.Processor<MlKitTextLine> {
  interface TextCandidateListener {
    void onTextCandidates(List<String> candidates);
  }

  private final GraphicOverlay<?> graphicOverlay;
  private final TextCandidateListener listener;

  MlKitOcrDetectorProcessor(GraphicOverlay<?> graphicOverlay, TextCandidateListener listener) {
    this.graphicOverlay = graphicOverlay;
    this.listener = listener;
  }

  @Override public void receiveDetections(Detector.Detections<MlKitTextLine> detections) {
    graphicOverlay.clear();
    SparseArray<MlKitTextLine> items = detections.getDetectedItems();
    List<String> candidates = new ArrayList<>();
    int width = detections.getFrameMetadata().getWidth();
    int height = detections.getFrameMetadata().getHeight();
    int rotation = detections.getFrameMetadata().getRotation();
    if ((rotation & 1) == 1) {
      int swapped = width;
      width = height;
      height = swapped;
    }
    OcrTitleRegion.Bounds titleRegion = OcrTitleRegion.forFrame(width, height);
    for (int index = 0; index < items.size(); index++) {
      MlKitTextLine line = items.valueAt(index);
      Rect box = line.getBoundingBox();
      if (box != null && titleRegion.containsCenter(box.left, box.top, box.right, box.bottom)) {
        addCandidate(candidates, line.getText());
      }
    }
    // Empty title frames are also delivered so consecutive physical copies can be separated.
    if (listener != null) listener.onTextCandidates(candidates);
  }

  private static void addCandidate(List<String> candidates, String rawText) {
    if (rawText == null) return;
    for (String line : rawText.split("\\r\\n|\\r|\\n")) {
      String cleaned = line.replace("|", "").trim();
      // Japanese titles can be only two ideographs, so retain the legacy lower bound.
      if (cleaned.length() >= 2 && cleaned.length() <= 80) candidates.add(cleaned);
    }
  }

  @Override public void release() {
    graphicOverlay.clear();
  }
}
