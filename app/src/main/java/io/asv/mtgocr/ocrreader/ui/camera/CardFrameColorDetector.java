package io.asv.mtgocr.ocrreader.ui.camera;

import io.asv.mtgocr.ocrreader.OcrTitleRegion;

/**
 * Reads the quiet center of the card's title bar before the OCR luminance mask is applied.
 *
 * This first calibration deliberately reports only white and blue: those title bars are far
 * enough apart in NV21 chroma to be useful under ordinary indoor lighting. Other frames remain
 * unknown, so color can never reject red, green, black, colorless or multicolored cards.
 */
final class CardFrameColorDetector {
    static final String UNKNOWN = "";
    static final String WHITE = "W";
    static final String BLUE = "U";

    private CardFrameColorDetector() { }

    static String detect(byte[] nv21, int width, int height, int rotation) {
        int lumaSize = width * height;
        if (width <= 0 || height <= 0 || nv21 == null || nv21.length < lumaSize + width) {
            return UNKNOWN;
        }
        int uprightWidth = (rotation & 1) == 1 ? height : width;
        int uprightHeight = (rotation & 1) == 1 ? width : height;
        OcrTitleRegion.Bounds title = OcrTitleRegion.forFrame(uprightWidth, uprightHeight);
        int titleWidth = title.right - title.left;
        int titleHeight = title.bottom - title.top;
        int sampleLeft = title.left + Math.round(titleWidth * .32f);
        int sampleRight = title.left + Math.round(titleWidth * .72f);
        int sampleTop = title.top + Math.round(titleHeight * .05f);
        int sampleBottom = title.top + Math.round(titleHeight * .34f);

        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int samples = 0;
        for (int rawY = 0; rawY < height; rawY += 4) {
            for (int rawX = 0; rawX < width; rawX += 4) {
                int uprightX;
                int uprightY;
                switch (rotation & 3) {
                    case 1:
                        uprightX = height - 1 - rawY;
                        uprightY = rawX;
                        break;
                    case 2:
                        uprightX = width - 1 - rawX;
                        uprightY = height - 1 - rawY;
                        break;
                    case 3:
                        uprightX = rawY;
                        uprightY = width - 1 - rawX;
                        break;
                    default:
                        uprightX = rawX;
                        uprightY = rawY;
                }
                if (uprightX < sampleLeft || uprightX > sampleRight ||
                        uprightY < sampleTop || uprightY > sampleBottom) continue;

                int y = nv21[rawY * width + rawX] & 0xff;
                if (y < 45 || y > 242) continue; // Ignore title glyphs, shadows and glare.
                int uv = lumaSize + (rawY >> 1) * width + (rawX & ~1);
                if (uv + 1 >= nv21.length) continue;
                int v = (nv21[uv] & 0xff) - 128;
                int u = (nv21[uv + 1] & 0xff) - 128;
                red += clamp(Math.round(y + 1.402f * v));
                green += clamp(Math.round(y - .344136f * u - .714136f * v));
                blue += clamp(Math.round(y + 1.772f * u));
                samples++;
            }
        }
        if (samples < 40) return UNKNOWN;
        return classifyAverageRgb(
                Math.round(red / (float) samples),
                Math.round(green / (float) samples),
                Math.round(blue / (float) samples));
    }

    static String classifyAverageRgb(int red, int green, int blue) {
        float[] hsv = rgbToHsv(red, green, blue);
        float hue = hsv[0];
        float saturation = hsv[1];
        float value = hsv[2];

        // Old white frames are cream rather than neutral white (the supplied Exile and Tariff
        // samples are around H=29-33°, S=.15-.28). Keep the window narrow enough to avoid gold.
        if (hue >= 18f && hue <= 58f && saturation >= .075f && saturation <= .36f &&
                value >= .42f && red > blue + 12) {
            return WHITE;
        }
        if (hue >= 178f && hue <= 246f && saturation >= .13f && value >= .30f &&
                blue > red + 12) {
            return BLUE;
        }
        return UNKNOWN;
    }

    private static float[] rgbToHsv(int red, int green, int blue) {
        float r = clamp(red) / 255f;
        float g = clamp(green) / 255f;
        float b = clamp(blue) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0f) {
            hue = 0f;
        } else if (max == r) {
            hue = 60f * (((g - b) / delta) % 6f);
        } else if (max == g) {
            hue = 60f * (((b - r) / delta) + 2f);
        } else {
            hue = 60f * (((r - g) / delta) + 4f);
        }
        if (hue < 0f) hue += 360f;
        return new float[] {hue, max == 0f ? 0f : delta / max, max};
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
