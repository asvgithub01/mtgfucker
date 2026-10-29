package io.asv.odinlab;
import android.graphics.*;import androidx.camera.core.ImageProxy;import java.nio.ByteBuffer;
/** Analyzer-thread-owned input buffer. Returned frame is independent and caller-owned. */
final class RgbaFrameConverter implements AutoCloseable {
 private Bitmap scratch;
 static Bitmap legacy(ImageProxy image){Bitmap full=image.toBitmap();Rect r=image.getCropRect();Matrix m=new Matrix();m.postRotate(image.getImageInfo().getRotationDegrees());Bitmap out=Bitmap.createBitmap(full,r.left,r.top,r.width(),r.height(),m,false);if(out!=full)full.recycle();return out;}
 Bitmap convert(ImageProxy image){var planes=image.getPlanes();int w=image.getWidth(),h=image.getHeight();
  if(image.getFormat()!=PixelFormat.RGBA_8888||planes.length!=1||planes[0].getPixelStride()!=4||planes[0].getRowStride()!=w*4)return legacy(image);
  ByteBuffer bytes=planes[0].getBuffer().duplicate();bytes.rewind();if(bytes.remaining()<w*h*4)return legacy(image);
  if(scratch==null||scratch.getWidth()!=w||scratch.getHeight()!=h){close();scratch=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}
  scratch.copyPixelsFromBuffer(bytes);Rect r=image.getCropRect();Matrix m=new Matrix();m.postRotate(image.getImageInfo().getRotationDegrees());Bitmap out=Bitmap.createBitmap(scratch,r.left,r.top,r.width(),r.height(),m,false);return out==scratch?scratch.copy(Bitmap.Config.ARGB_8888,false):out;
 }
 public void close(){if(scratch!=null){scratch.recycle();scratch=null;}}
}
