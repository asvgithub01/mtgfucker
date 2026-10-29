package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
import ai.onnxruntime.*;

/** Frozen saved crops, paired old/new gallery outputs, no camera or production access. */
final class RefreshReplay {
 static JSONObject query(OrtEnvironment env,Map<String,OrtSession> sessions,String variant,int[] pixels,int width,int height)throws Exception{
  boolean milo=variant.equals("milo");String model=milo?"milo":"diego";int views=variant.equals("diego_tta6")?3:1;boolean one=variant.equals("diego_upright1");double encodeMs=0,searchMs=0;Map<Integer,Double> scores=new HashMap<>(),oldScores=new HashMap<>();
  for(int view=0;view<views;view++){long t=System.nanoTime();int dx=(int)(width*(view*.02)),dy=(int)(height*(view*.02)),w=width-2*dx,h=height-2*dy;int[] p=pixels;if(view>0){p=new int[w*h];for(int y=0;y<h;y++)System.arraycopy(pixels,(y+dy)*width+dx,p,y*w,w);}
   float[] a=FullCatalog.encode(env,sessions.get(model+".onnx"),p,w,h,milo,false),b=one?a:FullCatalog.encode(env,sessions.get(model+".onnx"),p,w,h,milo,true);encodeMs+=CaptureEngine.ms(t);t=System.nanoTime();float[] flat=new float[a.length*2];System.arraycopy(a,0,flat,0,a.length);System.arraycopy(b,0,flat,a.length,b.length);
   try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(flat),new long[]{2,a.length});OrtSession.Result r=sessions.get(model+"-search.onnx").run(Collections.singletonMap("query",x))){for(int scope=0;scope<2;scope++){FloatBuffer sc=((OnnxTensor)r.get(scope*2)).getFloatBuffer();LongBuffer ix=((OnnxTensor)r.get(scope*2+1)).getLongBuffer();Map<Integer,Double> target=scope==0?scores:oldScores;while(ix.hasRemaining()){int index=(int)ix.get();double score=sc.get();target.put(index,Math.max(score,target.getOrDefault(index,-2.)));}}}
   searchMs+=CaptureEngine.ms(t);
  }
  JSONObject row=new JSONObject().put("encode_ms",encodeMs).put("search_ms",searchMs).put("total_ms",encodeMs+searchMs);
  for(String prefix:new String[]{"","base_"}){Map<Integer,Double> map=prefix.isEmpty()?scores:oldScores;List<Integer> keys=new ArrayList<>(map.keySet());keys.sort((a,b)->{int c=Double.compare(map.get(b),map.get(a));return c==0?Integer.compare(a,b):c;});JSONArray ix=new JSONArray(),sc=new JSONArray();for(int i=0;i<Math.min(5,keys.size());i++){int k=keys.get(i);ix.put(k);sc.put(map.get(k));}row.put(prefix+"indices",ix).put(prefix+"scores",sc);}
  return row;
 }
 static JSONObject run(Context c,Consumer<String> progress)throws Exception{
  if(!org.opencv.android.OpenCVLoader.initLocal())throw new IOException("OpenCV");org.opencv.core.Core.setNumThreads(1);File dir=new File(c.getFilesDir(),"catalog-replay-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");
  JSONObject spec=FullCatalog.json(c,"full-spec.json"),replay=FullCatalog.json(c,"refresh-spec.json");JSONArray qs=replay.getJSONArray("queries"),rows=new JSONArray();OrtEnvironment env=OrtEnvironment.getEnvironment();Map<String,OrtSession> sessions=new HashMap<>();String[] modes={"milo","diego","diego_upright1","diego_tta6"};int checked=0;double maxError=0;long peak=0;
  JSONObject report=new JSONObject().put("run",dir.getName()).put("model",android.os.Build.MODEL).put("catalog",spec.getString("catalog_version")).put("models",spec.getJSONObject("models")).put("count",spec.getInt("count")).put("base_count",spec.getInt("base_count")).put("threads",4).put("provider","CPU").put("rows",rows).put("queries",qs).put("timing_scope","preprocess+encode+exact old/new search; no decode/load/detection/warmup; 3 repetitions");
  try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
   options.setIntraOpNumThreads(4);options.setInterOpNumThreads(1);
   try{
    for(String name:new String[]{"milo.onnx","diego.onnx","milo-search.onnx","diego-search.onnx"}){progress.accept("Cargando "+name);sessions.put(name,env.createSession(FullCatalog.model(c,name,spec.getJSONObject("models").getString(name)).toString(),options));}
    for(int i=0;i<qs.length();i++){JSONObject q=qs.getJSONObject(i);Bitmap b;try(InputStream in=c.getAssets().open(q.getString("asset"))){b=BitmapFactory.decodeStream(in);}int w=b.getWidth(),h=b.getHeight();int[] pixels=new int[w*h];b.getPixels(pixels,0,w,0,0,w,h);b.recycle();
     for(int rep=(i==0?-1:0);rep<3;rep++)for(int j=0;j<modes.length;j++){String mode=modes[(j+i+rep+4)%4];progress.accept("Catálogo nuevo · "+(i+1)+"/"+qs.length()+" · "+mode);JSONObject row=query(env,sessions,mode,pixels,w,h);if(rep<0)continue;row.put("id",q.getString("id")).put("mode",mode).put("repeat",rep);rows.put(row);
      if(rep==0){JSONObject gold=replay.getJSONObject("variant_expected").getJSONArray(mode).getJSONObject(i);for(String prefix:new String[]{"","base_"})for(int k=0;k<5;k++){if(row.getJSONArray(prefix+"indices").getInt(k)!=gold.getJSONArray(prefix+"indices").getInt(k))throw new IOException("Top5 parity: "+q.getString("id")+" "+mode);double diff=Math.abs(row.getJSONArray(prefix+"scores").getDouble(k)-gold.getJSONArray(prefix+"scores").getDouble(k));maxError=Math.max(maxError,diff);if(diff>.0001)throw new IOException("Score parity "+diff);}checked++;}
     }
     peak=Math.max(peak,android.os.Debug.getPss());report.put("completed_queries",i+1).put("parity_checks",checked).put("max_score_error",maxError).put("peak_pss_kb",peak);CaptureEngine.save(dir,"partial.json",report);
    }
    report.put("complete",true);CaptureEngine.save(dir,"results.json",report);return report;
   }catch(Exception e){report.put("error",e.toString());CaptureEngine.save(dir,"error.json",report);throw e;}finally{for(OrtSession session:sessions.values())session.close();}
  }
 }
}
