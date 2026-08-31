package io.asv.mtgocr.ocrreader.ui.camera;

/** Normalizes the NV21 luminance plane before OCR without altering the visible camera preview. */
final class OcrLumaEnhancer {
    private OcrLumaEnhancer() { }

    static void enhance(byte[] nv21, int width, int height) {
        int pixels = Math.min(nv21.length, width * height);
        if (pixels <= 0) return;
        long sum = 0L;
        long sumSquares = 0L;
        int samples = 0;
        for (int index = 0; index < pixels; index += 16) {
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
        for (int index = 0; index < pixels; index++) nv21[index] = (byte) table[nv21[index] & 0xff];
    }
}
