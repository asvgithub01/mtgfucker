package io.asv.odinlab;
import android.graphics.Bitmap;
import ai.onnxruntime.*;
import java.nio.*;
import java.util.*;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
final class NeuralPose {
 static float[] input(Bitmap frame,boolean yolo){int w=frame.getWidth(),h=frame.getHeight();byte[] rgb;
  if(!yolo){int[] pixels=new int[w*h];frame.getPixels(pixels,0,w,0,0,w,h);rgb=Preprocess.corneliusExact(pixels,w,h,640);}
  else{Mat rgba=new Mat(),original=new Mat(),small=new Mat(),pad=new Mat();try{Utils.bitmapToMat(frame,rgba);Imgproc.cvtColor(rgba,original,Imgproc.COLOR_RGBA2RGB);double ratio=Math.min(640.0/w,640.0/h);int rw=(int)Math.rint(w*ratio),rh=(int)Math.rint(h*ratio),left=(int)Math.rint((640-rw)/2.0-.1),top=(int)Math.rint((640-rh)/2.0-.1);Imgproc.resize(original,small,new Size(rw,rh),0,0,Imgproc.INTER_LINEAR);Core.copyMakeBorder(small,pad,top,640-rh-top,left,640-rw-left,Core.BORDER_CONSTANT,new Scalar(114,114,114));rgb=new byte[640*640*3];pad.get(0,0,rgb);}finally{rgba.release();original.release();small.release();pad.release();}}
  int n=640*640;float[] x=new float[3*n];for(int c=0;c<3;c++)for(int i=0;i<n;i++)x[c*n+i]=(rgb[3*i+c]&255)/255f;return x;
 }
 static List<float[]> run(OrtEnvironment env,OrtSession session,float[] input,boolean yolo,int w,int h)throws Exception{try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,640,640});OrtSession.Result r=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),x))){
  if(yolo){float[][][] values=(float[][][])r.get(0).getValue();return PoseGeometry.yolo(values[0],w,h);}
  float[][] scores=(float[][])r.get("scores").get().getValue();float[][][][] points=(float[][][][])r.get("keypoints").get().getValue();return PoseGeometry.diego(scores[0],points[0]);
 }}
}
