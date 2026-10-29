package io.asv.odinlab;
import android.content.Context;
import android.graphics.*;
import java.nio.*;
import java.util.*;
import org.json.*;
import ai.onnxruntime.*;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

/** Single executor, persistent sessions. Detect all proposals, identify ONE selected card. */
final class LiveEngine implements AutoCloseable {
 private OdinIdentifier odin;private EsquinitasDetector esquinitas;
 private com.google.mlkit.vision.objects.ObjectDetector objects;
 final Map<String,OrtSession> sessions=new HashMap<>();final OrtEnvironment env=OrtEnvironment.getEnvironment();final JSONObject spec,poseSpec;final JSONArray ids,labels;final double loadMs;
 static final String[] COMBINATIONS={"Cornelius + Milo","Cornelius + Odin","Tmikonen + Milo","Tmikonen + Odin","YOLO Pose + Milo","YOLO Pose + Odin","Diego Pose + Milo","Diego Pose + Odin","OpenCV + Milo","OpenCV + Odin","OpenCV CLAHE + Milo","OpenCV CLAHE + Odin","","","","","","","","","","","Esquinitas EXP + Milo","Esquinitas EXP + Odin"};
 static final String[] DETECTORS={"cornelius","tmikonen","yolo-pose","diego-pose","opencv","opencv-clahe","lcnet050","mlkit-box","mlkit-lcnet","riftbound","fastweb-1.39","esquinitas-spatial01-EXP"};
 static final class Detection{final List<double[][]> quads=new ArrayList<>();double ms,prepareMs,inferenceMs;int rejected;double sharpness=Double.NaN;}
 LiveEngine(Context c)throws Exception{long t=System.nanoTime();odin=new OdinIdentifier(c);esquinitas=new EsquinitasDetector(c);COMBINATIONS[12]=c.getString(R.string.lcnet_milo);COMBINATIONS[13]=c.getString(R.string.lcnet_diego);COMBINATIONS[14]=c.getString(R.string.mlbox_milo);COMBINATIONS[15]=c.getString(R.string.mlbox_diego);COMBINATIONS[16]=c.getString(R.string.mlref_milo);COMBINATIONS[17]=c.getString(R.string.mlref_diego);COMBINATIONS[18]=c.getString(R.string.rift_milo);COMBINATIONS[19]=c.getString(R.string.rift_diego);COMBINATIONS[20]=c.getString(R.string.fastweb_milo);COMBINATIONS[21]=c.getString(R.string.fastweb_diego);if(!org.opencv.android.OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV");Core.setNumThreads(1);spec=FullCatalog.json(c,"full-spec.json");poseSpec=FullCatalog.json(c,"pose-models.json");JSONObject checks=FullCatalog.json(c,"checksums.json");ids=CaptureEngine.array(c,"full-ids.json");labels=CaptureEngine.array(c,"scan-labels.json");try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){options.setIntraOpNumThreads(4);options.setInterOpNumThreads(1);try{sessions.put("fastweb-single-1.39.onnx",openFastWeb(c));sessions.put("riftbound_regions.onnx",env.createSession(FullCatalog.model(c,"riftbound_regions.onnx",FullCatalog.json(c,"riftbound-model.json").getString("riftbound_regions.onnx")).toString(),options));sessions.put("lcnet050.onnx",env.createSession(FullCatalog.model(c,"lcnet050.onnx",FullCatalog.json(c,"lcnet-model.json").getString("lcnet050.onnx")).toString(),options));for(String n:new String[]{"cornelius.onnx","milo.onnx","milo-search.onnx","yolo-pose.onnx","diego-pose.onnx"})sessions.put(n,env.createSession(FullCatalog.model(c,n,n.endsWith("-pose.onnx")?poseSpec.getString(n):(n.equals("cornelius.onnx")?checks.getString(n):spec.getJSONObject("models").getString(n))).toString(),options));}catch(Exception e){close();throw e;}}loadMs=CaptureEngine.ms(t);}
 // ORT Android1.20 extended GatherSliceToSplitFusion creates duplicate node names.
 // Restrict only this session to BASIC; retain all other model/session settings.
 private OrtSession openFastWeb(Context c)throws Exception{
  try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()){
   opts.setIntraOpNumThreads(4);opts.setInterOpNumThreads(1);
   opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
   return env.createSession(FullCatalog.model(c,"fastweb-single-1.39.onnx",FullCatalog.json(c,"fastweb-model.json").getString("fastweb-single-1.39.onnx")).toString(),opts);
  }
 }
 Detection detect(Bitmap frame,boolean tm,boolean all)throws Exception{return detect(frame,tm?1:0,all);}
 Detection detect(Bitmap frame,int detector,boolean all)throws Exception{long t=System.nanoTime();int w=frame.getWidth(),h=frame.getHeight();Detection out=new Detection();List<float[]> raw=new ArrayList<>();
  if(detector==11){JSONObject r=esquinitas.run(frame);if(r.getDouble("presence")>=.5){JSONArray a=r.getJSONArray("corners");float[] q=new float[8];for(int i=0;i<8;i++)q[i]=(float)a.getDouble(i);raw.add(q);}else out.rejected++;out.inferenceMs=CaptureEngine.ms(t);}
  else if(detector==10){int[] pixels=new int[w*h];frame.getPixels(pixels,0,w,0,0,w,h);float[] input=Preprocess.tensor(Preprocess.corneliusExact(pixels,w,h,384));out.prepareMs=CaptureEngine.ms(t);long it=System.nanoTime();OrtSession model=sessions.get("fastweb-single-1.39.onnx");try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,384,384});OrtSession.Result result=model.run(Collections.singletonMap("image",x))){out.sharpness=((OnnxTensor)result.get("sharpness").get()).getFloatBuffer().get();FloatBuffer buf=((OnnxTensor)result.get("corners").get()).getFloatBuffer();float[] q=new float[buf.remaining()];buf.get(q);if(FastWebGate.accept(q,out.sharpness))raw.add(q);else out.rejected++;}out.inferenceMs=CaptureEngine.ms(it);}
  else if(detector==9){float[] input=NeuralPose.input(frame,true);out.prepareMs=CaptureEngine.ms(t);long it=System.nanoTime();OrtSession model=sessions.get("riftbound_regions.onnx");try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,640,640});OrtSession.Result result=model.run(Collections.singletonMap(model.getInputNames().iterator().next(),x))){raw=RiftGeometry.decode(((float[][][])result.get(0).getValue())[0],w,h);}out.inferenceMs=CaptureEngine.ms(it);}
  else if(detector==7||detector==8){
   if(objects==null)objects=com.google.mlkit.vision.objects.ObjectDetection.getClient(new com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions.Builder().setDetectorMode(com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().build());
   long it=System.nanoTime();var found=com.google.android.gms.tasks.Tasks.await(objects.process(com.google.mlkit.vision.common.InputImage.fromBitmap(frame,0)),30,java.util.concurrent.TimeUnit.SECONDS);out.inferenceMs=CaptureEngine.ms(it);
   for(var obj:found){android.graphics.Rect r=new android.graphics.Rect(obj.getBoundingBox());if(!r.intersect(0,0,w,h)||r.width()<2||r.height()<2)continue;
    if(detector==7){raw.add(new float[]{r.left/(float)w,r.top/(float)h,r.right/(float)w,r.top/(float)h,r.right/(float)w,r.bottom/(float)h,r.left/(float)w,r.bottom/(float)h});}
    else{int dx=Math.round(r.width()*.1f),dy=Math.round(r.height()*.1f);r.inset(-dx,-dy);r.intersect(0,0,w,h);Bitmap roi=Bitmap.createBitmap(frame,r.left,r.top,r.width(),r.height());try{for(float[] q:LcNetPose.run(env,sessions.get("lcnet050.onnx"),LcNetPose.input(roi))){for(int i=0;i<4;i++){q[2*i]=(r.left+q[2*i]*r.width())/w;q[2*i+1]=(r.top+q[2*i+1]*r.height())/h;}raw.add(q);}}finally{if(roi!=frame)roi.recycle();}}
   }
  }else if(detector==6){float[] x=LcNetPose.input(frame);out.prepareMs=CaptureEngine.ms(t);long it=System.nanoTime();raw=LcNetPose.run(env,sessions.get("lcnet050.onnx"),x);out.inferenceMs=CaptureEngine.ms(it);}
  else if(detector==4||detector==5){raw=LabCv.detect(frame,detector==5);out.prepareMs=CaptureEngine.ms(t);}
  else if(detector==2||detector==3){boolean yolo=detector==2;float[] x=NeuralPose.input(frame,yolo);out.prepareMs=CaptureEngine.ms(t);long it=System.nanoTime();raw=NeuralPose.run(env,sessions.get(yolo?"yolo-pose.onnx":"diego-pose.onnx"),x,yolo,w,h);out.inferenceMs=CaptureEngine.ms(it);}
  else if(detector==1){Mat rgba=new Mat(),bgr=new Mat(),small=new Mat();try{Utils.bitmapToMat(frame,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);int sw=w,sh=h;if(Math.min(w,h)>1000){if(w<h){sw=1000;sh=(int)((long)h*1000/w);}else{sh=1000;sw=(int)((long)w*1000/h);}}Imgproc.resize(bgr,small,new Size(sw,sh),0,0,Imgproc.INTER_AREA);TmikonenDetector.Result r=TmikonenDetector.detect(small,all?"all":"gray",true);out.rejected=r.geometryRejected;for(Polygon p:r.quads){float[] q=new float[8];Coordinate[] coords=TmikonenGeometry.points(p);for(int i=0;i<4;i++){q[2*i]=(float)(coords[i].x/sw);q[2*i+1]=(float)(coords[i].y/sh);}raw.add(q);}}finally{rgba.release();bgr.release();small.release();}}
  else{int[] pixels=new int[w*h];frame.getPixels(pixels,0,w,0,0,w,h);float[] input=Preprocess.tensor(Preprocess.corneliusExact(pixels,w,h,384));out.prepareMs=CaptureEngine.ms(t);long inferenceStart=System.nanoTime();OrtSession s=sessions.get("cornelius.onnx");try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(input),new long[]{1,3,384,384});OrtSession.Result r=s.run(Collections.singletonMap(s.getInputNames().iterator().next(),x))){FloatBuffer b=((OnnxTensor)r.get("corners").get()).getFloatBuffer();float[] q=new float[b.remaining()];b.get(q);raw.add(q);}out.inferenceMs=CaptureEngine.ms(inferenceStart);}
  for(float[] q:raw)try{out.quads.add(UprightQuad.order(q,w,h));}catch(IllegalArgumentException invalid){out.rejected++;}out.ms=CaptureEngine.ms(t);return out;
 }
 static int select(Detection d,float x,float y){int best=-1;double dist=Double.POSITIVE_INFINITY;for(int i=0;i<d.quads.size();i++){double v=UprightQuad.centerDistance(d.quads.get(i),x,y);if(v<dist){dist=v;best=i;}}return best;}
 static Bitmap crop(Bitmap frame,double[][] q){return crop(frame,q,640,896);}
 static Bitmap crop(Bitmap frame,double[][] q,int width,int height){Mat src=new Mat(),dst=new Mat(),transform=null;org.opencv.core.Point[] points=new org.opencv.core.Point[4];for(int i=0;i<4;i++)points[i]=new org.opencv.core.Point(q[i][0]*frame.getWidth(),q[i][1]*frame.getHeight());MatOfPoint2f from=new MatOfPoint2f(points),to=new MatOfPoint2f(new org.opencv.core.Point(0,0),new org.opencv.core.Point(width-1,0),new org.opencv.core.Point(width-1,height-1),new org.opencv.core.Point(0,height-1));try{Utils.bitmapToMat(frame,src);transform=Imgproc.getPerspectiveTransform(from,to);Imgproc.warpPerspective(src,dst,transform,new Size(width,height));Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,b);return b;}finally{src.release();dst.release();from.release();to.release();if(transform!=null)transform.release();}}
 JSONObject identify(Bitmap crop,boolean milo)throws Exception{if(!milo)return odin.identify(crop);long t=System.nanoTime();int w=crop.getWidth(),h=crop.getHeight();int[] p=new int[w*h];crop.getPixels(p,0,w,0,0,w,h);String name=milo?"milo":"diego";float[] z=FullCatalog.encode(env,sessions.get(name+".onnx"),p,w,h,milo,false);double encode=CaptureEngine.ms(t);t=System.nanoTime();
  // Search graph has two input slots. Duplicate one vector; encoder executes ONCE.
  float[] flat=new float[z.length*2];System.arraycopy(z,0,flat,0,z.length);System.arraycopy(z,0,flat,z.length,z.length);JSONArray top=new JSONArray();
  try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(flat),new long[]{2,z.length});OrtSession.Result r=sessions.get(name+"-search.onnx").run(Collections.singletonMap("query",x))){FloatBuffer scores=((OnnxTensor)r.get(0)).getFloatBuffer();LongBuffer indices=((OnnxTensor)r.get(1)).getLongBuffer();while(indices.hasRemaining()){int index=(int)indices.get();top.put(new JSONObject().put("id",ids.getString(index)).put("label",labels.getString(index)).put("cosine",scores.get()));}}
  return new JSONObject().put("model",name).put("orientation","upright-only").put("encoder_passes",1).put("encode_ms",encode).put("search_ms",CaptureEngine.ms(t)).put("top",top);
 }
 static JSONArray jsonQuads(Detection d)throws Exception{JSONArray out=new JSONArray();for(double[][] q:d.quads){JSONArray a=new JSONArray();for(double[] p:q)a.put(new JSONArray().put(p[0]).put(p[1]));out.put(a);}return out;}
 @Override public void close(){if(esquinitas!=null){esquinitas.close();esquinitas=null;}if(odin!=null){odin.close();odin=null;}if(objects!=null){objects.close();objects=null;}for(OrtSession s:sessions.values())try{s.close();}catch(Exception ignored){}sessions.clear();}
}
