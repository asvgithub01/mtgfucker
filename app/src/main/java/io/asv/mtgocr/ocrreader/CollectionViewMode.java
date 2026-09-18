package io.asv.mtgocr.ocrreader;

/** Display choices for the cards shown in the personal library. */
public final class CollectionViewMode {
  public static final int LIST = 0;
  public static final int CARD_GRID = 1;
  public static final int ARTWORK_WIDE = 2;
  public static final int ARTWORK_GRID = 3;

  private CollectionViewMode() { }

  public static int sanitize(int mode) {
    return mode >= LIST && mode <= ARTWORK_GRID ? mode : LIST;
  }

  public static boolean usesArtwork(int mode) {
    return mode == ARTWORK_WIDE || mode == ARTWORK_GRID;
  }

  public static boolean usesTwoColumns(int mode) {
    return mode == CARD_GRID || mode == ARTWORK_GRID;
  }
}
