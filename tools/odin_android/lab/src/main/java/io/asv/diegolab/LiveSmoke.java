package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.io.*;
import org.json.*;
/** Device integration check; static assets, NOT a camera latency benchmark. */
final class LiveSmoke {
 static void run(Context c)throws Exception{
  File dir=new File(c.getFilesDir(),"live-smoke-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");JSONArray rows=new JSONArray();
  try(LiveEngine engine=new LiveEngine(c)){
   for(String asset:new String[]{"raw0.png","multi16.png"}){Bitmap b;try(InputStream in=c.getAssets().open(asset)){b=BitmapFactory.decodeStream(in);}try{
    for(boolean tm:new boolean[]{false,true}){LiveEngine.Detection d=engine.detect(b,tm,false);int selected=LiveEngine.select(d,.5f,.5f);JSONObject row=new JSONObject().put("asset",asset).put("detector",tm?"tmikonen":"cornelius").put("quads",LiveEngine.jsonQuads(d)).put("selected",selected).put("detect_ms",d.ms);if(selected>=0){Bitmap crop=LiveEngine.crop(b,d.quads.get(selected));try{row.put("milo",engine.identify(crop,true)).put("diego",engine.identify(crop,false));CaptureEngine.png(new File(dir,asset+"-"+tm+"-crop.png"),crop);}finally{crop.recycle();}}rows.put(row);}
   }finally{b.recycle();}}
  }
  CaptureEngine.save(dir,"results.json",new JSONObject().put("scope","static integration smoke; not camera validation or accuracy").put("rows",rows));
 }
}
