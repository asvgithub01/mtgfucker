package io.asv.mtgocr.ocrreader.ui.camera;

/** Associates color analysis with the detector callback running on the same camera worker. */
public final class CardFrameColorContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CardFrameColorContext() { }

    static void set(String color) {
        CURRENT.set(color == null ? CardFrameColorDetector.UNKNOWN : color);
    }

    public static String current() {
        String color = CURRENT.get();
        return color == null ? CardFrameColorDetector.UNKNOWN : color;
    }

    static void clear() {
        CURRENT.remove();
    }
}
