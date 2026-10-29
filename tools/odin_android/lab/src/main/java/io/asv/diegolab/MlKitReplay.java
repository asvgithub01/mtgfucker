package io.asv.odinlab;
import android.content.Context;import android.graphics.*;import java.io.*;import java.util.*;import java.util.concurrent.TimeUnit;import java.security.MessageDigest;import org.json.*;
import com.google.android.gms.tasks.Tasks;import com.google.mlkit.vision.common.InputImage;import com.google.mlkit.vision.objects.*;import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
/** Baseline rectangles only: never convert these into claimed perspective corners. */
final class MlKitReplay {
 static JSONObject run(Context c)throws Exception{
  File dir=new File(c.getFilesDir(),"mlkit-replay-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");JSONArray rows=new JSONArray();JSONObject report=new JSONObject().put("run",dir.getName()).put("complete",false).put("scope","ML Kit on-device generic object rectangles, maximum5; not card corners. Single image mode, classification disabled. Warmup +3 repeats, Task roundtrip included; input decode excluded.").put("rows",rows);
  ObjectDetector detector=ObjectDetection.getClient(new ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().build());
  try{JSONArray gold=FullCatalog.json(c,"pose-replay.json").getJSONArray("rows");for(int i=0;i<gold.length();i++){
   JSONObject g=gold.getJSONObject(i);String name=g.getString("input");byte[] bytes;
   if(name.startsWith("asset:")){try(InputStream in=c.getAssets().open(name.substring(6))){bytes=in.readAllBytes();}}else{File f=new File(c.getFilesDir(),name.substring(6));if(!name.startsWith("files/")||!f.getCanonicalPath().startsWith(c.getFilesDir().getCanonicalPath()+File.separator))throw new IOException("Input path");bytes=java.nio.file.Files.readAllBytes(f.toPath());}
   StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!hash.toString().equals(g.getString("sha256")))throw new IOException("Input SHA");
   Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);try{InputImage image=InputImage.fromBitmap(b,0);JSONObject row=new JSONObject().put("input",name).put("sha256",hash.toString());rows.put(row);
    try{Tasks.await(detector.process(image),30,TimeUnit.SECONDS);JSONArray times=new JSONArray();List<DetectedObject> objects=null;for(int rep=0;rep<3;rep++){long t=System.nanoTime();objects=Tasks.await(detector.process(image),30,TimeUnit.SECONDS);times.put(CaptureEngine.ms(t));}JSONArray boxes=new JSONArray();Bitmap overlay=b.copy(Bitmap.Config.ARGB_8888,true);try{Canvas canvas=new Canvas(overlay);Paint paint=new Paint();paint.setColor(Color.MAGENTA);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);for(DetectedObject object:objects){Rect box=object.getBoundingBox();boxes.put(new JSONArray().put(box.left).put(box.top).put(box.right).put(box.bottom));canvas.drawRect(box,paint);}CaptureEngine.png(new File(dir,i+"-boxes.png"),overlay);}finally{overlay.recycle();}row.put("boxes",boxes).put("times_ms",times).put("overlay",i+"-boxes.png");}
    catch(Exception e){row.put("error",e.toString()).put("note","Initial model may need downloading through Google Play services; error is not a missed detection.");}
    CaptureEngine.save(dir,"results.json",report);
   }finally{b.recycle();}
  }}finally{detector.close();}
  report.put("complete",true);CaptureEngine.save(dir,"results.json",report);return report;
 }
}
