package io.asv.odinlab;
import android.content.Context;import android.graphics.Bitmap;import java.io.*;import org.json.*;
final class RiftPhotoRun {
 static JSONObject run(Context c,LiveEngine engine,Bitmap frame,File dir)throws Exception{
  CaptureEngine.png(new File(dir,"input.png"),frame);JSONArray rows=new JSONArray();JSONObject report=new JSONObject().put("complete",false).put("input_sha256",DocumentDiegoEngine.sha(new File(dir,"input.png"))).put("rift_model",FullCatalog.json(c,"riftbound-model.json")).put("catalog",engine.spec.getString("catalog_version")).put("orientation","upright-only").put("note","All accepted portrait boxes identified; Cornelius is one proposal, not multicard recall. One measured pass; initialization excluded. No collection insertion.").put("rows",rows);CaptureEngine.save(dir,"results.json",report);
  for(int detector:new int[]{0,9}){String name=LiveEngine.DETECTORS[detector];long start=System.nanoTime();LiveEngine.Detection d=engine.detect(frame,detector,false);JSONObject row=new JSONObject().put("detector",name).put("detect_ms",d.ms).put("rejected",d.rejected).put("quads",LiveEngine.jsonQuads(d));JSONArray cards=new JSONArray();row.put("cards",cards);rows.put(row);DetectorComparison.overlay(dir,name+"-overlay",frame,LiveEngine.jsonQuads(d));double identifyMs=0;
   for(int i=0;i<d.quads.size();i++){Bitmap crop=LiveEngine.crop(frame,d.quads.get(i));try{long t=System.nanoTime();JSONObject id=engine.identify(crop,false);double ms=CaptureEngine.ms(t);identifyMs+=ms;String file=name+"-"+i+".png";CaptureEngine.png(new File(dir,file),crop);cards.put(new JSONObject().put("index",i).put("crop",file).put("identify_ms",ms).put("diego",id));}finally{crop.recycle();}}
   row.put("identify_all_ms",identifyMs).put("wall_ms_includes_saving",CaptureEngine.ms(start));CaptureEngine.save(dir,"results.json",report);
  }report.put("complete",true);CaptureEngine.save(dir,"results.json",report);return report;
 }
}
