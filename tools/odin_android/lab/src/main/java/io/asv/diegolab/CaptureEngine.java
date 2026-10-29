package io.asv.odinlab;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;
import ai.onnxruntime.*;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/** Paired manual capture. No production imports, networking, or collection access. */
final class CaptureEngine {
 static void save(File dir,String name,JSONObject value)throws Exception {
  File temp=new File(dir,name+".tmp");Files.write(temp.toPath(),value.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Files.move(temp.toPath(),new File(dir,name).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
 }
 static void png(File file,Bitmap b)throws Exception{try(OutputStream out=new FileOutputStream(file)){if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG");}}
 static JSONArray array(Context c,String name)throws Exception{try(InputStream in=c.getAssets().open(name)){return new JSONArray(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
 static double ms(long t){return (System.nanoTime()-t)/1e6;}
 static JSONObject run(Context c,File dir,Bitmap image,boolean alreadyCropped)throws Exception {
  if(!org.opencv.android.OpenCVLoader.initLocal())throw new IOException("OpenCV");Core.setNumThreads(1);
  OrtEnvironment env=OrtEnvironment.getEnvironment();Map<String,OrtSession> sessions=new HashMap<>();
  JSONObject spec=FullCatalog.json(c,"full-spec.json"),checks=FullCatalog.json(c,"checksums.json");JSONArray ids=array(c,"full-ids.json"),labels=array(c,"scan-labels.json");
  JSONObject report=new JSONObject().put("schema",1).put("branch","codex/dinov2-benchmark").put("device",android.os.Build.MODEL).put("count",ids.length()).put("catalog_version",spec.optString("catalog_version","baseline-109931")).put("models",spec.getJSONObject("models")).put("cornelius_sha",checks.getString("cornelius.onnx")).put("crop_policy",alreadyCropped?"input_is_crop":"cornelius").put("orientation","upright-only").put("encoder_passes",1).put("rating_status","unreviewed").put("timing_scope","decode/load/warmup excluded; detector and warp separate; identification includes preprocess + upright-only single inference + search");
  Bitmap crop=null;long load=System.nanoTime();
  try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){
   o.setIntraOpNumThreads(4);o.setInterOpNumThreads(1);
   try{
    for(String name:new String[]{"diego.onnx","diego-search.onnx","milo.onnx","milo-search.onnx","cornelius.onnx"}){
     String sha=name.equals("cornelius.onnx")?checks.getString(name):spec.getJSONObject("models").getString(name);
     sessions.put(name,env.createSession(FullCatalog.model(c,name,sha).toString(),o));
    }
    report.put("load_ms",ms(load));int w=image.getWidth(),h=image.getHeight();int[] p=new int[w*h];image.getPixels(p,0,w,0,0,w,h);
    long t=System.nanoTime();
    if(alreadyCropped){crop=image.copy(Bitmap.Config.ARGB_8888,false);report.put("detector_ms",0);report.put("warp_ms",0);}
    else {
     float[] input=Preprocess.tensor(Preprocess.view(p,w,h,0,false,384,false));float[] q;
     try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,384,384});OrtSession.Result r=sessions.get("cornelius.onnx").run(Collections.singletonMap(sessions.get("cornelius.onnx").getInputNames().iterator().next(),x))){FloatBuffer b=((OnnxTensor)r.get("corners").get()).getFloatBuffer();q=new float[b.remaining()];b.get(q);report.put("sharpness",((OnnxTensor)r.get("sharpness").get()).getFloatBuffer().get());}
     report.put("detector_ms",ms(t));t=System.nanoTime();
     double[][] ordered=UprightQuad.order(q,w,h);JSONArray quad=new JSONArray();org.opencv.core.Point[] points=new org.opencv.core.Point[4];
     Bitmap overlay=image.copy(Bitmap.Config.ARGB_8888,true);Canvas canvas=new Canvas(overlay);Paint paint=new Paint();paint.setColor(Color.YELLOW);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(3,w/250f));android.graphics.Path path=new android.graphics.Path();
     for(int i=0;i<4;i++){double x=ordered[i][0]*w,y=ordered[i][1]*h;points[i]=new org.opencv.core.Point(x,y);quad.put(new JSONArray().put(ordered[i][0]).put(ordered[i][1]));if(i==0)path.moveTo((float)x,(float)y);else path.lineTo((float)x,(float)y);}path.close();canvas.drawPath(path,paint);report.put("quad",quad);
     Mat src=new Mat(),dst=new Mat();MatOfPoint2f from=new MatOfPoint2f(points),to=new MatOfPoint2f(new org.opencv.core.Point(0,0),new org.opencv.core.Point(639,0),new org.opencv.core.Point(639,895),new org.opencv.core.Point(0,895));Mat transform=null;
     try{Utils.bitmapToMat(image,src);transform=Imgproc.getPerspectiveTransform(from,to);Imgproc.warpPerspective(src,dst,transform,new Size(640,896));crop=Bitmap.createBitmap(640,896,Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,crop);}finally{src.release();dst.release();from.release();to.release();if(transform!=null)transform.release();}
     report.put("warp_ms",ms(t));png(new File(dir,"overlay.png"),overlay);overlay.recycle();
    }
    png(new File(dir,"crop.png"),crop);int cw=crop.getWidth(),ch=crop.getHeight();int[] pixels=new int[cw*ch];crop.getPixels(pixels,0,cw,0,0,cw,ch);
    JSONArray rows=new JSONArray();report.put("rows",rows);boolean firstDiego=(dir.getName().hashCode()&1)==0;
    for(int rep=-1;rep<3;rep++)for(int j=0;j<2;j++){
     String mode=((j+rep+2+(firstDiego?0:1))%2==0)?"diego":"milo";boolean milo=mode.equals("milo");t=System.nanoTime();
     float[] a=FullCatalog.encode(env,sessions.get(mode+".onnx"),pixels,cw,ch,milo,false),b=a;
     double enc=ms(t);t=System.nanoTime();float[] flat=new float[a.length*2];System.arraycopy(a,0,flat,0,a.length);System.arraycopy(b,0,flat,a.length,b.length);
     JSONArray top=new JSONArray();double search;
     try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(flat),new long[]{2,a.length});OrtSession.Result r=sessions.get(mode+"-search.onnx").run(Collections.singletonMap("query",x))){FloatBuffer scores=((OnnxTensor)r.get(0)).getFloatBuffer();LongBuffer ix=((OnnxTensor)r.get(1)).getLongBuffer();search=ms(t);for(int k=0;k<5;k++){int index=(int)ix.get();top.put(new JSONObject().put("id",ids.getString(index)).put("label",labels.getString(index)).put("cosine",scores.get()));}}
     if(rep>=0)rows.put(new JSONObject().put("mode",mode).put("repeat",rep).put("encode_ms",enc).put("search_ms",search).put("total_ms",enc+search).put("top",top));
    }
    report.put("thermal",((android.os.PowerManager)c.getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus());save(dir,"result.json",report);return report;
   }finally{for(OrtSession s:sessions.values())s.close();if(crop!=null)crop.recycle();}
  }
 }
}
