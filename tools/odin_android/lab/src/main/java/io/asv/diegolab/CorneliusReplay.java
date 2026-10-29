package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import ai.onnxruntime.*;
import org.json.*;
/** Alternating old/new paired replay of lab-owned photos. Never writes source captures. */
final class CorneliusReplay {
 static void files(File dir,List<File> result){File[] list=dir.listFiles();if(list==null)return;Arrays.sort(list);for(File f:list){if(f.isDirectory())files(f,result);else if(f.getName().equals("input.png")||f.getName().equals("frame.png"))result.add(f);}}
 static float[] infer(OrtEnvironment env,OrtSession session,float[] tensor)throws Exception{try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(tensor),new long[]{1,3,384,384});OrtSession.Result r=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),x))){FloatBuffer b=((OnnxTensor)r.get("corners").get()).getFloatBuffer();float[] q=new float[b.remaining()];b.get(q);return q;}}
 static void run(Context c)throws Exception{
  File out=new File(c.getFilesDir(),"cornelius-replay-"+System.currentTimeMillis());if(!out.mkdirs())throw new IOException("mkdir");List<File> inputs=new ArrayList<>();files(new File(c.getFilesDir(),"captures"),inputs);files(new File(c.getFilesDir(),"live"),inputs);if(inputs.isEmpty())throw new IOException("No lab captures");JSONArray rows=new JSONArray();JSONObject report=new JSONObject().put("scope","Sony static paired replay; not camera latency").put("policy","same original pixels, original resize vs bit-exact fused RGB; 3 warmups +5 alternating repetitions").put("rows",rows);
  OrtEnvironment env=OrtEnvironment.getEnvironment();String sha=FullCatalog.json(c,"checksums.json").getString("cornelius.onnx");report.put("model_sha256",sha);
  try(OrtSession.SessionOptions opt=new OrtSession.SessionOptions()){opt.setIntraOpNumThreads(4);opt.setInterOpNumThreads(1);try(OrtSession session=env.createSession(FullCatalog.model(c,"cornelius.onnx",sha).toString(),opt)){
   for(File input:inputs){Bitmap image=BitmapFactory.decodeFile(input.toString());if(image==null)throw new IOException(input.toString());try{int w=image.getWidth(),h=image.getHeight();int[] pixels=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);byte[] base=Preprocess.view(pixels,w,h,0,false,384,false),fast=Preprocess.corneliusExact(pixels,w,h,384);if(!Arrays.equals(base,fast))throw new AssertionError("RGB parity");float[] reference=infer(env,session,Preprocess.tensor(base));JSONArray measures=new JSONArray();
    for(int rep=-3;rep<5;rep++)for(int j=0;j<2;j++){boolean optimized=(rep+j+4)%2==1;long start=System.nanoTime();int[] p=new int[w*h];image.getPixels(p,0,w,0,0,w,h);float[] tensor=Preprocess.tensor(optimized?Preprocess.corneliusExact(p,w,h,384):Preprocess.view(p,w,h,0,false,384,false));double prep=CaptureEngine.ms(start);long t=System.nanoTime();float[] q=infer(env,session,tensor);double inference=CaptureEngine.ms(t),total=CaptureEngine.ms(start);if(!Arrays.equals(reference,q))throw new AssertionError("Corners parity");if(rep>=0)measures.put(new JSONObject().put("optimized",optimized).put("prepare_ms",prep).put("inference_ms",inference).put("total_ms",total));}
    JSONObject row=new JSONObject().put("input",input.toString()).put("width",w).put("height",h).put("rgb_exact",true).put("corners_exact",true).put("measurements",measures);JSONArray corners=new JSONArray();for(float v:reference)corners.put(v);row.put("corners",corners);rows.put(row);CaptureEngine.save(out,"results.json",report);
   }finally{image.recycle();}}
  }}
 }
}
