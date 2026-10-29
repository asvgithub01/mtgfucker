package io.asv.odinlab;
import android.content.Context;import android.graphics.*;import java.io.*;import java.security.MessageDigest;import java.util.*;import org.json.*;
/** Frozen originals; reduction preserves FOV, and does NOT emulate a CameraX sensor mode. */
final class ResolutionReplay {
 static final String[] MODES={"pose-original","pose-1440","pose-960","pose-1440-clahe","opencv","opencv-clahe","warp-320","query-clahe"};
 static final class Sample {JSONObject json;Bitmap crop;LiveEngine.Detection detection;}
 static Sample sample(LiveEngine engine,Bitmap original,int mode)throws Exception{
  long start=System.nanoTime();Bitmap input=original,filtered=null,crop=null;try{
   if(mode==1||mode==3)input=LabCv.reduced(original,1440);else if(mode==2)input=LabCv.reduced(original,960);
   if(mode==3)filtered=LabCv.clahe(input);
   double inputMs=CaptureEngine.ms(start);LiveEngine.Detection d=engine.detect(filtered!=null?filtered:input,mode==4?4:mode==5?5:3,false);
   JSONObject result=new JSONObject().put("input_ms",inputMs).put("detect_ms",d.ms).put("prepare_ms",d.prepareMs).put("inference_ms",d.inferenceMs).put("width",input.getWidth()).put("height",input.getHeight()).put("quads",LiveEngine.jsonQuads(d));
   int selected=LiveEngine.select(d,.5f,.5f);result.put("selected",selected);
   if(selected>=0){long t=System.nanoTime();crop=LiveEngine.crop(input,d.quads.get(selected),mode==6?320:640,mode==6?448:896);if(mode==7){Bitmap enhanced=LabCv.clahe(crop);crop.recycle();crop=enhanced;}result.put("warp_filter_ms",CaptureEngine.ms(t));result.put("diego",engine.identify(crop,false));}
   result.put("total_ms",CaptureEngine.ms(start));Sample out=new Sample();out.json=result;out.crop=crop;out.detection=d;crop=null;return out;
  }finally{if(crop!=null)crop.recycle();if(filtered!=null)filtered.recycle();if(input!=original)input.recycle();}
 }
 static JSONObject run(Context c)throws Exception{
  File dir=new File(c.getFilesDir(),"resolution-replay-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");JSONArray cases=FullCatalog.json(c,"resolution-cases.json").getJSONArray("rows"),rows=new JSONArray();JSONObject report=new JSONObject().put("run",dir.getName()).put("complete",false).put("scope","Static full pipeline on same originals; INTER_AREA longest1440/960 preserves FOV, not a real CameraX sensor profile. 1warmup+3alternating repeats. Model pose640 and identification448x320 fixed. Baseline is not groundtruth.").put("gate","No default change unless all baseline crops retained, paired IoU>=.98 and no identification regression; manual review and Sony required.").put("rows",rows);
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
