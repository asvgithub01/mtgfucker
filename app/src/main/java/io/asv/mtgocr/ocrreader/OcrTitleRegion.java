package io.asv.mtgocr.ocrreader;

/** Geometry shared by the visible card guide, camera-frame masking and OCR result filtering. */
public final class OcrTitleRegion {
  public static final float CARD_ASPECT_RATIO = 63f / 88f;

  private OcrTitleRegion() { }

  public static Bounds cardForFrame(int width, int height) {
    float cardHeight = Math.min(height * .72f, width * .92f / CARD_ASPECT_RATIO);
    float cardWidth = cardHeight * CARD_ASPECT_RATIO;
    return new Bounds(Math.round((width - cardWidth) / 2f), Math.round((height - cardHeight) / 2f),
        Math.round((width + cardWidth) / 2f), Math.round((height + cardHeight) / 2f));
  }

  public static Bounds forFrame(int width, int height) {
    return region(width, height, .03f, .025f, .97f, .22f);
  }

  public static Bounds rulesForFrame(int width, int height) {
    return region(width, height, .055f, .59f, .945f, .89f);
  }

  private static Bounds region(int width, int height, float left, float top, float right, float bottom) {
    float cardHeight = Math.min(height * .72f, width * .92f / CARD_ASPECT_RATIO);
    float cardWidth = cardHeight * CARD_ASPECT_RATIO;
    float cardLeft = (width - cardWidth) / 2f;
    float cardTop = (height - cardHeight) / 2f;
    return new Bounds(Math.round(cardLeft + cardWidth * left), Math.round(cardTop + cardHeight * top),
        Math.round(cardLeft + cardWidth * right), Math.round(cardTop + cardHeight * bottom));
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
