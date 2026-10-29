package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
import ai.onnxruntime.*;

final class FullCatalog {
 static JSONObject json(Context c,String f)throws Exception{try(InputStream s=c.getAssets().open(f)){return new JSONObject(new String(s.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
 static File model(Context c,String name,String expected)throws Exception{
  File f=new File(c.getFilesDir(),name);if(!f.getParentFile().isDirectory()&&!f.getParentFile().mkdirs())throw new IOException("mkdir");MessageDigest hash=MessageDigest.getInstance("SHA-256");
  try(InputStream in=c.getAssets().open(name);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){hash.update(b,0,n);out.write(b,0,n);}}
  StringBuilder h=new StringBuilder();for(byte v:hash.digest())h.append(String.format(java.util.Locale.ROOT,"%02x",v&255));if(!h.toString().equals(expected))throw new IOException("Model SHA: "+name);return f;
 }
 static float[] encode(OrtEnvironment env,OrtSession s,int[] p,int w,int h,boolean milo,boolean rotate)throws Exception{
  byte[] rgb=milo?Preprocess.view(p,w,h,0,rotate,448,false):Preprocess.diegoView(p,w,h,rotate,true);
  float[] input=milo?Preprocess.tensor(rgb):Preprocess.diegoTensor(rgb);
  try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,448,milo?448:320});OrtSession.Result r=s.run(Collections.singletonMap(s.getInputNames().iterator().next(),x))){FloatBuffer b=((OnnxTensor)r.get(0)).getFloatBuffer();float[] v=new float[b.remaining()];b.get(v);Ranking.normalize(v);return v;}
 }
 static JSONObject run(Context c,Consumer<String> progress)throws Exception{
  if(!org.opencv.android.OpenCVLoader.initLocal())throw new IOException("OpenCV");org.opencv.core.Core.setNumThreads(1);
  JSONObject spec=json(c,"full-spec.json"),corpus=json(c,"corpus.json");JSONArray qs=corpus.getJSONArray("queries");
  OrtEnvironment env=OrtEnvironment.getEnvironment();Map<String,OrtSession> sessions=new HashMap<>();JSONArray rows=new JSONArray();JSONObject loads=new JSONObject();long peak=0;double minCos=1;int rankingMatch=0;double maxScoreDiff=0;
  try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){
   o.setIntraOpNumThreads(4);o.setInterOpNumThreads(1);
   try{
    for(String name:new String[]{"diego.onnx","diego-search.onnx","milo.onnx","milo-search.onnx"}){progress.accept("Cargando "+name);File f=model(c,name,spec.getJSONObject("models").getString(name));long t=System.nanoTime();sessions.put(name,env.createSession(f.toString(),o));loads.put(name,(System.nanoTime()-t)/1e6);}
    int thermalStart=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus();
    for(int i=0;i<qs.length();i++){
     JSONObject q=qs.getJSONObject(i);Bitmap b;try(InputStream in=c.getAssets().open(q.getString("asset"))){b=BitmapFactory.decodeStream(in);}int w=b.getWidth(),h=b.getHeight();int[] pixels=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);b.recycle();
     for(int rep=(i==0?-2:0);rep<3;rep++)for(int j=0;j<2;j++){
      String mode=((i+rep+j+4)%2==0)?"diego":"milo";boolean milo=mode.equals("milo");progress.accept("Catálogo "+spec.getInt("count")+" · "+(i+1)+"/49 · "+mode);long t=System.nanoTime();
      float[][] vectors={encode(env,sessions.get(mode+".onnx"),pixels,w,h,milo,false),encode(env,sessions.get(mode+".onnx"),pixels,w,h,milo,true)};
      double encodeMs=(System.nanoTime()-t)/1e6;t=System.nanoTime();float[] flat=new float[vectors[0].length*2];System.arraycopy(vectors[0],0,flat,0,vectors[0].length);System.arraycopy(vectors[1],0,flat,vectors[0].length,vectors[1].length);
      float[] scores,baseScores;long[] indices,baseIndices;
      try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(flat),new long[]{2,vectors[0].length});OrtSession.Result result=sessions.get(mode+"-search.onnx").run(Collections.singletonMap("query",x))){FloatBuffer s=((OnnxTensor)result.get(0)).getFloatBuffer();scores=new float[s.remaining()];s.get(scores);LongBuffer ix=((OnnxTensor)result.get(1)).getLongBuffer();indices=new long[ix.remaining()];ix.get(indices);FloatBuffer bs=((OnnxTensor)result.get(2)).getFloatBuffer();baseScores=new float[bs.remaining()];bs.get(baseScores);LongBuffer bi=((OnnxTensor)result.get(3)).getLongBuffer();baseIndices=new long[bi.remaining()];bi.get(baseIndices);}
      double searchMs=(System.nanoTime()-t)/1e6;if(rep<0)continue;
      JSONObject row=new JSONObject().put("id",q.getString("id")).put("mode",mode).put("repeat",rep).put("encode_ms",encodeMs).put("search_ms",searchMs).put("total_ms",encodeMs+searchMs);JSONArray ix=new JSONArray(),sc=new JSONArray(),bi=new JSONArray(),bs=new JSONArray();for(int k=0;k<5;k++){ix.put(indices[k]);sc.put(scores[k]);bi.put(baseIndices[k]);bs.put(baseScores[k]);}row.put("indices",ix).put("scores",sc).put("base_indices",bi).put("base_scores",bs);rows.put(row);
      if(rep==0){JSONObject e=spec.getJSONObject("expected").getJSONArray(mode).getJSONObject(i);boolean match=true;for(int k=0;k<5;k++){match&=indices[k]==e.getJSONArray("indices").getLong(k);maxScoreDiff=Math.max(maxScoreDiff,Math.abs(scores[k]-e.getJSONArray("scores").getDouble(k)));}if(match)rankingMatch++;
       for(int v=0;v<2;v++){JSONArray ref=e.getJSONArray("embedding").getJSONArray(v);double dot=0,aa=0,bb=0;for(int k=0;k<vectors[v].length;k++){double a=vectors[v][k],z=ref.getDouble(k);dot+=a*z;aa+=a*a;bb+=z*z;}double cos=dot/Math.sqrt(aa*bb);minCos=Math.min(minCos,cos);if(cos<.9999)throw new IOException("Strict parity "+cos);}
      }
     }
     peak=Math.max(peak,Debug.getPss());
    }
    JSONObject r=new JSONObject().put("rows",rows).put("count",spec.getInt("count")).put("base_count",spec.getInt("base_count")).put("model",Build.MODEL).put("soc",Build.SOC_MODEL).put("ort",env.getVersion()).put("threads",4).put("provider","CPU").put("load_ms",loads).put("peak_pss_kb",peak).put("min_cosine",minCos).put("top5_ranking_matches",rankingMatch).put("max_top_score_diff",maxScoreDiff).put("thermal_start",thermalStart).put("thermal_end",((PowerManager)c.getSystemService(Context.POWER_SERVICE)).getCurrentThermalStatus());
    Files.write(new File(c.getFilesDir(),"full-latest.json").toPath(),r.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));return r;
   }finally{for(OrtSession s:sessions.values())s.close();}
  }
 }
}
