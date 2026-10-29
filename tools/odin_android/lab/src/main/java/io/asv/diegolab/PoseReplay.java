package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
/** Read-only inputs; new output directory each run. Static timing is not camera latency. */
final class PoseReplay {
 static JSONObject run(Context c)throws Exception {
  File dir=new File(c.getFilesDir(),"pose-replay-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");
  JSONArray gold=FullCatalog.json(c,"pose-replay.json").getJSONArray("rows"),rows=new JSONArray();
  JSONObject report=new JSONObject().put("run",dir.getName()).put("complete",false).put("expected",gold.length()).put("rows",rows).put("scope","Static replay; one warmup and three alternating detector repeats. Includes preprocessing; excludes decode, load, warp and identification. Proposals are NOT accuracy.");
  try(LiveEngine engine=new LiveEngine(c)) {
   report.put("models",engine.poseSpec);
   for(int n=0;n<gold.length();n++) {
    JSONObject g=gold.getJSONObject(n);String path=g.getString("input");byte[] bytes;
    if(path.startsWith("asset:")){try(InputStream in=c.getAssets().open(path.substring(6))){bytes=in.readAllBytes();}}
    else {File f=new File(c.getFilesDir(),path.substring(6));if(!path.startsWith("files/")||!f.getCanonicalPath().startsWith(c.getFilesDir().getCanonicalPath()+File.separator))throw new IOException("Input path");bytes=java.nio.file.Files.readAllBytes(f.toPath());}
    StringBuilder hash=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",v&255));if(!hash.toString().equals(g.getString("sha256")))throw new IOException("Input SHA: "+path);
    Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IOException("Decode");
    try {
     LiveEngine.Detection[] last=new LiveEngine.Detection[4];JSONArray[] times={new JSONArray(),new JSONArray(),new JSONArray(),new JSONArray()};
     for(int d=0;d<4;d++)engine.detect(b,d,false);
     for(int rep=0;rep<3;rep++)for(int offset=0;offset<4;offset++){int d=(offset+rep+n)%4;last[d]=engine.detect(b,d,false);times[d].put(last[d].ms);}
     JSONObject row=new JSONObject().put("input",path).put("sha256",hash.toString()).put("models",new JSONObject());rows.put(row);
     for(int d=0;d<4;d++) {
      String name=LiveEngine.DETECTORS[d],prefix=n+"-"+name;LiveEngine.Detection found=last[d];JSONArray quads=LiveEngine.jsonQuads(found);
      JSONObject r=new JSONObject().put("quads",quads).put("times_ms",times[d]).put("prepare_ms",found.prepareMs).put("inference_ms",found.inferenceMs).put("rejected",found.rejected).put("overlay",prefix+".png");row.getJSONObject("models").put(name,r);
      DetectorComparison.overlay(dir,prefix,b,quads);
      if(d>=2){float[] input=NeuralPose.input(b,d==2);java.nio.ByteBuffer buffer=java.nio.ByteBuffer.allocate(input.length*4).order(java.nio.ByteOrder.LITTLE_ENDIAN);buffer.asFloatBuffer().put(input);StringBuilder ih=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(buffer.array()))ih.append(String.format(Locale.ROOT,"%02x",v&255));r.put("input_sha256",ih.toString());if(n==12&&d==2)java.nio.file.Files.write(new File(dir,"yolo-case12-input.f32").toPath(),buffer.array());JSONArray expected=g.getJSONObject("models").getJSONObject(name).getJSONArray("quads");double error=0;boolean same=expected.length()==quads.length();if(same)for(int i=0;i<quads.length();i++)for(int j=0;j<4;j++)for(int k=0;k<2;k++)error=Math.max(error,Math.abs(quads.getJSONArray(i).getJSONArray(j).getDouble(k)-expected.getJSONArray(i).getJSONArray(j).getDouble(k)));r.put("pc_count_equal",same).put("pc_max_abs",error).put("pc_parity",same&&error<=.0001);}
      int selected=LiveEngine.select(found,.5f,.5f);r.put("selected",selected);
      if(selected>=0){Bitmap crop=LiveEngine.crop(b,found.quads.get(selected));try{r.put("crop",prefix+"-crop.png");CaptureEngine.png(new File(dir,prefix+"-crop.png"),crop);r.put("milo",engine.identify(crop,true)).put("diego",engine.identify(crop,false));}finally{crop.recycle();}}
     }
     CaptureEngine.save(dir,"results.json",report);
    }finally{b.recycle();}
   }
  }
  report.put("complete",true);CaptureEngine.save(dir,"results.json",report);return report;
 }
}
