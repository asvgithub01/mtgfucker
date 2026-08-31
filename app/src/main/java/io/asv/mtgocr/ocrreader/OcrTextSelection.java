package io.asv.mtgocr.ocrreader;

/** Keeps manual OCR taps scoped to the card-name line instead of the whole detected card. */
final class OcrTextSelection {
  private OcrTextSelection() { }

  static String firstPhrase(String recognizedText) {
    if (recognizedText == null) return "";
    String[] lines = recognizedText.split("\\r\\n|\\r|\\n");
    for (String line : lines) {
      String cleaned = line.replace("(", "").replace("|", "").trim();
      if (!cleaned.isEmpty()) return cleaned;
    }
    return "";
  }
}
