package io.asv.mtgocr.ocrreader;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;

import java.nio.ByteBuffer;

/**
 * Lets the new ML Kit Japanese recognizer run inside the existing CameraSource.
 *
 * The bundled Japanese model recognizes both Japanese and Latin text. Detection is deliberately
 * awaited on CameraSource's worker thread: the legacy camera can then keep dropping stale frames,
 * while the NV21 callback buffer remains owned by this detector until ML Kit has finished with it.
 */
final class MlKitJapaneseTextDetector extends Detector<MlKitTextLine> {
  private static final String TAG = "MlKitJapaneseOcr";
  private final TextRecognizer recognizer = TextRecognition.getClient(
      new JapaneseTextRecognizerOptions.Builder().build());
  private volatile boolean released;

  @Override public SparseArray<MlKitTextLine> detect(Frame frame) {
    SparseArray<MlKitTextLine> detected = new SparseArray<>();
    if (released || frame == null) return detected;

    Frame.Metadata metadata = frame.getMetadata();
    try {
      InputImage image = inputImage(frame, metadata);
      Text result = Tasks.await(recognizer.process(image));
      int id = 0;
      for (Text.TextBlock block : result.getTextBlocks()) {
        for (Text.Line line : block.getLines()) {
          String value = line.getText();
          if (value != null && !value.trim().isEmpty()) {
            detected.append(id++, new MlKitTextLine(value, line.getBoundingBox()));
          }
        }
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    } catch (Exception error) {
      Log.w(TAG, "No se pudo procesar el frame con ML Kit", error);
    }
    return detected;
  }

  private InputImage inputImage(Frame frame, Frame.Metadata metadata) {
    ByteBuffer grayscale = frame.getGrayscaleImageData();
    int width = metadata.getWidth();
    int height = metadata.getHeight();
    int rotationDegrees = metadata.getRotation() * 90;

    // A Frame built from CameraSource NV21 keeps the complete camera byte[] behind the grayscale
    // view. Re-wrapping that array avoids allocating a 1.3 MP Bitmap on every preview frame.
    if (grayscale != null && grayscale.hasArray() &&
        grayscale.array().length >= width * height * 3 / 2) {
      ByteBuffer nv21 = ByteBuffer.wrap(grayscale.array());
      return InputImage.fromByteBuffer(
          nv21, width, height, rotationDegrees, InputImage.IMAGE_FORMAT_NV21);
    }

    // Defensive fallback for a future CameraSource that supplies a direct/luma-only buffer.
    ByteBuffer luma = grayscale == null ? ByteBuffer.allocate(width * height) : grayscale.duplicate();
    luma.rewind();
    int[] pixels = new int[width * height];
    for (int index = 0; index < pixels.length && luma.hasRemaining(); index++) {
      int value = luma.get() & 0xff;
      pixels[index] = Color.rgb(value, value, value);
    }
    Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    return InputImage.fromBitmap(bitmap, rotationDegrees);
  }

  @Override public void release() {
    released = true;
    recognizer.close();
    super.release();
  }
}
