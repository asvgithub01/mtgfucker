package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import ai.onnxruntime.*;
import org.json.*;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.locationtech.jts.geom.*;

final class DetectorComparison {
 static final String[] MODES={"cornelius","tm-gray-original","tm-gray-reject-frame","tm-all-original","tm-all-reject-frame"};
 static JSONArray quads(List<Polygon> polygons,int w,int h)throws Exception{JSONArray out=new JSONArray();for(Polygon p:polygons){JSONArray q=new JSONArray();for(Coordinate c:TmikonenGeometry.points(p))q.put(new JSONArray().put(c.x/w).put(c.y/h));out.put(q);}return out;}
 static void overlay(File dir,String name,Bitmap image,JSONArray quads)throws Exception{Bitmap b=image.copy(Bitmap.Config.ARGB_8888,true);try{Canvas c=new Canvas(b);Paint paint=new Paint();paint.setColor(Color.YELLOW);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);Paint text=new Paint();text.setColor(Color.RED);text.setTextSize(28);
  for(int i=0;i<quads.length();i++){JSONArray q=quads.getJSONArray(i);android.graphics.Path path=new android.graphics.Path();float x0=0,y0=0;for(int j=0;j<q.length();j++){float x=(float)q.getJSONArray(j).getDouble(0)*b.getWidth(),y=(float)q.getJSONArray(j).getDouble(1)*b.getHeight();if(j==0){path.moveTo(x,y);x0=x;y0=y;}else path.lineTo(x,y);}path.close();c.drawPath(path,paint);c.drawText(""+(i+1),x0,y0,text);}CaptureEngine.png(new File(dir,name+".png"),b);
 }finally{b.recycle();}}
 static JSONObject run(Context context,File dir,Bitmap source)throws Exception{
  if(!org.opencv.android.OpenCVLoader.initLocal())throw new IOException("OpenCV");Core.setNumThreads(1);Mat rgba=new Mat(),small=new Mat(),bgr=new Mat();Bitmap image=null;
  JSONObject report=new JSONObject().put("implementation","tmikonen Java/JTS Android port (not Python)").put("upstream_revision","78728930bd8494aeed892e87200f5e06ac90ac51").put("opencv",Core.VERSION).put("device",android.os.Build.MODEL).put("branch","codex/dinov2-benchmark").put("timing_scope","same input; preprocessing+detector+NMS; excludes common resize/decode/model load/overlay; one warmup then 3 repeats").put("limitations","Cornelius returns one proposal, not a multicard detector. Tmikonen port uses JTS instead of GEOS; geometry failures counted, >32 vertices skipped, >30s aborts. No pHash or crop-factor warp in detection timings; proposals are not confirmed cards.");
  try{long resize=System.nanoTime();Utils.bitmapToMat(source,rgba);int sw=source.getWidth(),sh=source.getHeight(),w=sw,h=sh;if(Math.min(sw,sh)>1000){if(sw<=sh){w=1000;h=(int)((long)sh*1000/sw);}else{h=1000;w=(int)((long)sw*1000/sh);}}Imgproc.resize(rgba,small,new Size(w,h),0,0,Imgproc.INTER_AREA);image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Utils.matToBitmap(small,image);Imgproc.cvtColor(small,bgr,Imgproc.COLOR_RGBA2BGR);int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);report.put("common_prepare_ms",CaptureEngine.ms(resize)).put("width",w).put("height",h);CaptureEngine.png(new File(dir,"detector-input.png"),image);
   JSONObject checks=FullCatalog.json(context,"checksums.json");OrtEnvironment env=OrtEnvironment.getEnvironment();try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
    options.setIntraOpNumThreads(4);options.setInterOpNumThreads(1);long load=System.nanoTime();try(OrtSession session=env.createSession(FullCatalog.model(context,"cornelius.onnx",checks.getString("cornelius.onnx")).toString(),options)){
     report.put("cornelius_load_ms",CaptureEngine.ms(load));JSONObject modes=new JSONObject();report.put("modes",modes);
     for(String mode:MODES){JSONObject entry=new JSONObject();modes.put(mode,entry);JSONArray times=new JSONArray(),found=new JSONArray();int rejected=0,raw=0;
      try{for(int rep=-1;rep<3;rep++){long t=System.nanoTime();
       if(mode.equals("cornelius")){float[] input=Preprocess.tensor(Preprocess.view(pixels,w,h,0,false,384,false));try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,384,384});OrtSession.Result result=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),x))){FloatBuffer buffer=((OnnxTensor)result.get("corners").get()).getFloatBuffer();float[] q=new float[buffer.remaining()];buffer.get(q);entry.put("sharpness",((OnnxTensor)result.get("sharpness").get()).getFloatBuffer().get());JSONArray quad=new JSONArray();boolean finite=q.length==8;for(float value:q)finite&=Float.isFinite(value);found=new JSONArray();if(finite){Coordinate[] ordered=new Coordinate[4];for(int j=0;j<4;j++)ordered[j]=new Coordinate(q[2*j],q[2*j+1]);for(Coordinate point:TmikonenGeometry.order(ordered))quad.put(new JSONArray().put(point.x).put(point.y));found.put(quad);}raw=found.length();}}
       else{TmikonenDetector.Result result=TmikonenDetector.detect(bgr,mode.contains("-all-")?"all":"gray",mode.endsWith("reject-frame"));found=quads(result.quads,w,h);rejected=result.geometryRejected;raw=result.rawCandidates;}
       if(rep>=0)times.put(CaptureEngine.ms(t));
      }
      double[] sorted={times.getDouble(0),times.getDouble(1),times.getDouble(2)};Arrays.sort(sorted);entry.put("quads",found).put("proposals",found.length()).put("raw_candidates",raw).put("geometry_rejected",rejected).put("times_ms",times).put("median_ms",sorted[1]);overlay(dir,mode,image,found);
      }catch(Exception e){entry.put("error",e.toString());}
      CaptureEngine.save(dir,"detectors.json",report);
     }
    }
   }return report;
  }finally{rgba.release();small.release();bgr.release();if(image!=null)image.recycle();}
 }
}
