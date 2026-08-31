package io.asv.mtgocr.ocrreader;

/** Geometry shared by the visible card guide, camera-frame masking and OCR result filtering. */
public final class OcrTitleRegion {
  public static final float CARD_ASPECT_RATIO = 63f / 88f;

  private OcrTitleRegion() { }

  public static Bounds forFrame(int width, int height) {
    float cardHeight = Math.min(height * .72f, width * .92f / CARD_ASPECT_RATIO);
    float cardWidth = cardHeight * CARD_ASPECT_RATIO;
    float cardLeft = (width - cardWidth) / 2f;
    float cardTop = (height - cardHeight) / 2f;
    return new Bounds(
        Math.round(cardLeft + cardWidth * .03f),
        Math.round(cardTop + cardHeight * .025f),
        Math.round(cardLeft + cardWidth * .97f),
        Math.round(cardTop + cardHeight * .22f)
    );
  }

  public static final class Bounds {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    Bounds(int left, int top, int right, int bottom) {
      this.left = left;
      this.top = top;
      this.right = right;
      this.bottom = bottom;
    }

    public boolean contains(int x, int y) {
      return x >= left && x <= right && y >= top && y <= bottom;
    }

    public boolean containsCenter(int left, int top, int right, int bottom) {
      return contains((left + right) / 2, (top + bottom) / 2);
    }
  }
}
