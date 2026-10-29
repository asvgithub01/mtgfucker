package io.asv.odinlab;
import android.graphics.Bitmap;import ai.onnxruntime.*;import java.nio.*;import java.util.*;
import org.opencv.android.Utils;import org.opencv.core.*;import org.opencv.imgproc.Imgproc;
/** DocAligner point model: BGR stretch256, /255; no crop or ImageNet normalization. */
final class LcNetPose {
 static float[] input(Bitmap bitmap){Mat rgba=new Mat(),bgr=new Mat(),small=new Mat();try{
  Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);Imgproc.resize(bgr,small,new Size(256,256),0,0,Imgproc.INTER_LINEAR);
  byte[] bytes=new byte[256*256*3];small.get(0,0,bytes);float[] x=new float[bytes.length];int n=256*256;
  for(int c=0;c<3;c++)for(int i=0;i<n;i++)x[c*n+i]=(bytes[i*3+c]&255)/255f;return x;
 }finally{rgba.release();bgr.release();small.release();}}
 static List<float[]> run(OrtEnvironment env,OrtSession session,float[] input)throws Exception{
  try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,256,256});OrtSession.Result r=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),x))){
   float presence=((OnnxTensor)r.get("has_obj").get()).getFloatBuffer().get();
   List<float[]> out=new ArrayList<>();if(Float.isFinite(presence)&&presence>.5f){FloatBuffer b=((OnnxTensor)r.get("points").get()).getFloatBuffer();if(b.remaining()!=8)throw new IllegalStateException("LCNet points shape");float[] q=new float[8];b.get(q);out.add(q);}return out;
  }
 }
}
