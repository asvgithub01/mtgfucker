package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
import ai.onnxruntime.*;

final class Parity {
 static byte[] bytes(float[] x){ByteBuffer b=ByteBuffer.allocate(x.length*4).order(ByteOrder.LITTLE_ENDIAN);b.asFloatBuffer().put(x);return b.array();}
 static float[] infer(OrtEnvironment env,OrtSession s,float[] x)throws Exception{
  try(OnnxTensor t=OnnxTensor.createTensor(env,FloatBuffer.wrap(x),new long[]{1,3,448,320});OrtSession.Result r=s.run(Collections.singletonMap(s.getInputNames().iterator().next(),t))){
   FloatBuffer b=((OnnxTensor)r.get(0)).getFloatBuffer();float[] v=new float[b.remaining()];b.get(v);Ranking.normalize(v);return v;
  }
 }
 static JSONObject run(Context c,Consumer<String> progress)throws Exception{
  if(!org.opencv.android.OpenCVLoader.initLocal())throw new IOException("OpenCV");
  JSONObject spec;try(InputStream in=c.getAssets().open("parity.json")){spec=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
  File dir=new File(c.getFilesDir(),"parity-"+System.currentTimeMillis());dir.mkdirs();
  File model=new File(c.getFilesDir(),"diego.onnx");if(!Benchmark.hash(Files.readAllBytes(model.toPath())).equals(spec.getString("model_sha")))throw new IOException("Model SHA");
  OrtEnvironment env=OrtEnvironment.getEnvironment();JSONArray rows=new JSONArray();double min=1;int rgbOk=0,tensorOk=0,sourceOk=0;
  try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){
   o.setIntraOpNumThreads(4);o.setInterOpNumThreads(1);
   try(OrtSession s=env.createSession(model.toString(),o)){
    JSONArray qs=spec.getJSONArray("rows");
    for(int i=0;i<qs.length();i++){
     JSONObject q=qs.getJSONObject(i);progress.accept("Paridad "+(i+1)+"/49");Bitmap b;try(InputStream in=c.getAssets().open(q.getString("asset"))){b=BitmapFactory.decodeStream(in);}
     int w=b.getWidth(),h=b.getHeight();int[] p=new int[w*h];b.getPixels(p,0,w,0,0,w,h);b.recycle();byte[] source=new byte[w*h*3];
     for(int k=0;k<p.length;k++){source[3*k]=(byte)(p[k]>>16);source[3*k+1]=(byte)(p[k]>>8);source[3*k+2]=(byte)p[k];}
     boolean src=Benchmark.hash(source).equals(q.getString("source_sha"));if(src)sourceOk++;
     for(int v=0;v<2;v++){
      byte[] rgb=Preprocess.diegoView(p,w,h,v==1,true);float[] input=Preprocess.diegoTensor(rgb),embedding=infer(env,s,input);
      JSONObject expected=q.getJSONArray("views").getJSONObject(v);boolean rh=Benchmark.hash(rgb).equals(expected.getString("rgb_sha")),th=Benchmark.hash(bytes(input)).equals(expected.getString("tensor_sha"));if(rh)rgbOk++;if(th)tensorOk++;
      JSONArray e=expected.getJSONArray("embedding");double dot=0,aa=0,bb=0,max=0;
      for(int k=0;k<embedding.length;k++){double x=embedding[k],y=e.getDouble(k);dot+=x*y;aa+=x*x;bb+=y*y;max=Math.max(max,Math.abs(x-y));}
      double cos=dot/Math.sqrt(aa*bb);min=Math.min(min,cos);
      rows.put(new JSONObject().put("query",i).put("view",v).put("source",src).put("rgb",rh).put("tensor",th).put("cosine",cos).put("max_abs",max));
      if(i==0&&v==0){
       byte[] legacy=Preprocess.diegoView(p,w,h,false,false);float[] old=Preprocess.tensor(legacy);float[] oldOut=infer(env,s,old);
       Files.write(new File(dir,"legacy.rgb").toPath(),legacy);Files.write(new File(dir,"legacy.tensor").toPath(),bytes(old));Files.write(new File(dir,"legacy.embedding").toPath(),bytes(oldOut));Files.write(new File(dir,"exact.rgb").toPath(),rgb);
      }
     }
    }
   }
  }
  JSONObject r=new JSONObject().put("rows",rows).put("source_matches",sourceOk).put("rgb_matches",rgbOk).put("tensor_matches",tensorOk).put("min_cosine",min).put("dir",dir.getName()).put("opencv",org.opencv.core.Core.VERSION);
  Files.write(new File(dir,"results.json").toPath(),r.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(new File(c.getFilesDir(),"parity-latest.json").toPath(),r.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;
 }
}
