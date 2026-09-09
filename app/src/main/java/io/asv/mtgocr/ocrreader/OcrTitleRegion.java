package io.asv.mtgocr.ocrreader;

/** Geometry shared by the visible card guide, camera-frame masking and OCR result filtering. */
public final class OcrTitleRegion {
  public static final float CARD_ASPECT_RATIO = 63f / 88f;

  private OcrTitleRegion() { }

  public static Bounds forFrame(int width, int height) {
    return boundsForCard(width, height, .03f, .025f, .97f, .22f);
  }

  /** Inner printed-name strip, excluding artwork and dark card edges from contrast statistics. */
  public static Bounds contrastSampleForFrame(int width, int height) {
    return boundsForCard(width, height, .08f, .025f, .92f, .105f);
  }

  private static Bounds boundsForCard(int width, int height, float leftFraction,
      float topFraction, float rightFraction, float bottomFraction) {
    float cardHeight = Math.min(height * .72f, width * .92f / CARD_ASPECT_RATIO);
    float cardWidth = cardHeight * CARD_ASPECT_RATIO;
    float cardLeft = (width - cardWidth) / 2f;
    float cardTop = (height - cardHeight) / 2f;
    return new Bounds(
        Math.round(cardLeft + cardWidth * leftFraction),
        Math.round(cardTop + cardHeight * topFraction),
        Math.round(cardLeft + cardWidth * rightFraction),
        Math.round(cardTop + cardHeight * bottomFraction)
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
