package io.asv.odinlab;
import android.content.Context;import android.graphics.*;import java.io.*;import java.security.MessageDigest;import java.util.*;import org.json.*;
/** Frozen originals; reduction preserves FOV, and does NOT emulate a CameraX sensor mode. */
final class MlObjectReplay {
 static final String[] MODES={"cornelius","mlkit-box","mlkit-lcnet"};
 static final class Sample {JSONObject json;Bitmap crop;LiveEngine.Detection detection;}
 static Sample sample(LiveEngine engine,Bitmap original,int mode)throws Exception{
  long start=System.nanoTime();LiveEngine.Detection d=engine.detect(original,mode==0?0:(mode==1?7:8),false);
  JSONObject result=new JSONObject().put("detect_ms",d.ms).put("quads",LiveEngine.jsonQuads(d));
  int selected=LiveEngine.select(d,.5f,.5f);result.put("selected",selected);Bitmap crop=null;
  if(selected>=0){crop=LiveEngine.crop(original,d.quads.get(selected));try{result.put("diego",engine.identify(crop,false));}catch(Exception e){crop.recycle();throw e;}}
  result.put("total_ms",CaptureEngine.ms(start));Sample out=new Sample();out.json=result;out.crop=crop;out.detection=d;return out;
 }
 static JSONObject run(Context c)throws Exception{
  File dir=new File(c.getFilesDir(),"mlobject-replay-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");JSONArray cases=FullCatalog.json(c,"resolution-cases.json").getJSONArray("rows"),rows=new JSONArray();JSONObject report=new JSONObject().put("run",dir.getName()).put("complete",false).put("scope","ML Kit SINGLE_IMAGE rectangles and ML Kit ROI10% + LCNet050 versus Cornelius, unchanged frozen captures. 1warmup+3alternating repeats. Diego upright; static replay not camera latency.").put("gate","Experimental option only; no default change. Rectangle is not four learned corners. Refiner has no rectangle fallback. Review clipping; static is not handheld.").put("rows",rows);
  try(LiveEngine engine=new LiveEngine(c)){
   for(int n=0;n<cases.length();n++){
    JSONObject g=cases.getJSONObject(n);String path=g.getString("input");byte[] bytes;
    if(path.startsWith("asset:")){try(InputStream in=c.getAssets().open(path.substring(6))){bytes=in.readAllBytes();}}else{File f=new File(c.getFilesDir(),path.substring(6));if(!path.startsWith("files/")||!f.getCanonicalPath().startsWith(c.getFilesDir().getCanonicalPath()+File.separator))throw new IOException("Input path");bytes=java.nio.file.Files.readAllBytes(f.toPath());}
    StringBuilder sha=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(bytes))sha.append(String.format(Locale.ROOT,"%02x",v&255));if(!sha.toString().equals(g.getString("sha256")))throw new IOException("Input SHA "+path);
    Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IOException("Decode");
    try{JSONObject modes=new JSONObject(),row=new JSONObject().put("input",path).put("sha256",sha.toString()).put("modes",modes);rows.put(row);for(String name:MODES)modes.put(name,new JSONObject().put("samples",new JSONArray()));
     for(int rep=-1;rep<3;rep++)for(int offset=0;offset<MODES.length;offset++){
      int mode=(offset+n+rep+1)%MODES.length;Sample s=sample(engine,b,mode);try{if(rep>=0){JSONObject entry=modes.getJSONObject(MODES[mode]);entry.getJSONArray("samples").put(s.json);if(rep==2){String prefix=n+"-"+MODES[mode];entry.put("quads",s.json.getJSONArray("quads")).put("overlay",prefix+".png");DetectorComparison.overlay(dir,prefix,b,s.json.getJSONArray("quads"));if(s.crop!=null){CaptureEngine.png(new File(dir,prefix+"-crop.png"),s.crop);entry.put("crop",prefix+"-crop.png");}}}}finally{if(s.crop!=null)s.crop.recycle();}
     }
     CaptureEngine.save(dir,"results.json",report);
    }finally{b.recycle();}
   }
  }
  report.put("complete",true);CaptureEngine.save(dir,"results.json",report);return report;
 }
}
