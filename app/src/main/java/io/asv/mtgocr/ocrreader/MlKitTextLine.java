package io.asv.mtgocr.ocrreader;

import android.graphics.Rect;

/** Immutable ML Kit result adapted to the legacy camera detector pipeline. */
final class MlKitTextLine {
  private final String text;
  private final Rect boundingBox;

  MlKitTextLine(String text, Rect boundingBox) {
    this.text = text;
    this.boundingBox = boundingBox == null ? null : new Rect(boundingBox);
  }

  String getText() {
    return text;
  }

  Rect getBoundingBox() {
    return boundingBox == null ? null : new Rect(boundingBox);
  }
}
