package io.asv.mtgocr.ocrreader.ui.camera;

import io.asv.mtgocr.ocrreader.OcrTitleRegion;

/** Normalizes the NV21 luminance plane before OCR without altering the visible camera preview. */
final class OcrLumaEnhancer {
    private OcrLumaEnhancer() { }

    static void enhanceExperimental(byte[] nv21, int width, int height, int rotation) {
        int uw = (rotation & 1) == 1 ? height : width;
        int uh = (rotation & 1) == 1 ? width : height;
        OcrTitleRegion.Bounds title = OcrTitleRegion.forFrame(uw, uh);
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (isInsideTitle(x, y, width, height, rotation, title)) {
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        if (right < left || bottom < top) { enhance(nv21, width, height, rotation); return; }
        int w = right - left + 1, h = bottom - top + 1;
        byte[] roi = new byte[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(nv21, (top + y) * width + left, roi, y * w, w);
        byte[] result;
        try { result = io.asv.mtgocr.ocrreader.OcrImageEnhancement.claheBytes(roi, w, h); }
        catch (RuntimeException | LinkageError error) { result = null; }
        // Preserve the old mask/rules-region behavior, replacing ONLY title preprocessing.
        enhance(nv21, width, height, rotation);
        if (result != null) for (int y = 0; y < h; y++)
            System.arraycopy(result, y * w, nv21, (top + y) * width + left, w);
    }

    static void enhance(byte[] nv21, int width, int height, int rotation) {
        int pixels = Math.min(nv21.length, width * height);
        if (pixels <= 0) return;
        int uprightWidth = (rotation & 1) == 1 ? height : width;
        int uprightHeight = (rotation & 1) == 1 ? width : height;
        OcrTitleRegion.Bounds title = OcrTitleRegion.forFrame(uprightWidth, uprightHeight);
        OcrTitleRegion.Bounds rules = OcrTitleRegion.rulesForFrame(uprightWidth, uprightHeight);
        long sum = 0L;
        long sumSquares = 0L;
        int samples = 0;
        for (int index = 0; index < pixels; index += 16) {
            int x = index % width;
            int y = index / width;
            if (!isInsideTitle(x, y, width, height, rotation, title)) continue;
            int value = nv21[index] & 0xff;
            sum += value;
            sumSquares += (long) value * value;
            samples++;
        }
        if (samples == 0) return;
        double mean = sum / (double) samples;
        double variance = Math.max(0d, sumSquares / (double) samples - mean * mean);
        double deviation = Math.sqrt(variance);
        double contrast = deviation < 28d ? 1.58d : 1.24d;
        // Bright white/cream frames are deliberately pulled away from clipping; very dark scenes
        // are lifted. Black title glyphs then separate more clearly from the old card frame.
        double targetMean = mean > 170d ? 154d : (mean < 72d ? 92d : mean);
        int[] table = new int[256];
        for (int value = 0; value < table.length; value++) {
            table[value] = (int) Math.max(0d, Math.min(255d, (value - mean) * contrast + targetMean));
        }
        for (int index = 0; index < pixels; index++) {
            int x = index % width;
            int y = index / width;
            nv21[index] = isInsideTitle(x, y, width, height, rotation, title)
                    ? (byte) table[nv21[index] & 0xff]
                    : isInsideTitle(x, y, width, height, rotation, rules) ? nv21[index] : (byte) 128;
        }
    }

    private static boolean isInsideTitle(int x, int y, int width, int height, int rotation,
            OcrTitleRegion.Bounds title) {
        int uprightX;
        int uprightY;
        switch (rotation & 3) {
            case 1:
                uprightX = height - 1 - y;
                uprightY = x;
                break;
            case 2:
                uprightX = width - 1 - x;
                uprightY = height - 1 - y;
                break;
            case 3:
                uprightX = y;
                uprightY = width - 1 - x;
                break;
            default:
                uprightX = x;
                uprightY = y;
        }
        return title.contains(uprightX, uprightY);
    }
}
