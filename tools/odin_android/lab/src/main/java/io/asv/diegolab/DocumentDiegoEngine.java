package io.asv.odinlab;
import android.content.Context;import android.graphics.Bitmap;import ai.onnxruntime.*;import java.nio.*;import java.io.*;import java.util.*;import java.security.*;import org.json.*;
/** Only Diego encoder/search. No detector or collection access. Worker-thread confined. */
final class DocumentDiegoEngine implements AutoCloseable {
 final OrtEnvironment env=OrtEnvironment.getEnvironment();OrtSession encoder,search;final JSONArray ids,labels;final JSONObject spec;final double loadMs;
 DocumentDiegoEngine(Context c)throws Exception{long t=System.nanoTime();if(!org.opencv.android.OpenCVLoader.initLocal())throw new IOException("OpenCV");
  org.opencv.core.Core.setNumThreads(1);spec=FullCatalog.json(c,"full-spec.json");ids=CaptureEngine.array(c,"full-ids.json");labels=CaptureEngine.array(c,"scan-labels.json");
  try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){o.setIntraOpNumThreads(4);o.setInterOpNumThreads(1);
   try{encoder=env.createSession(model(c,"diego.onnx",spec.getJSONObject("models").getString("diego.onnx")).toString(),o);search=env.createSession(model(c,"diego-search.onnx",spec.getJSONObject("models").getString("diego-search.onnx")).toString(),o);}catch(Exception e){close();throw e;}}
  loadMs=CaptureEngine.ms(t);
 }
 static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}StringBuilder s=new StringBuilder();for(byte v:d.digest())s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 private static synchronized File model(Context c,String name,String expected)throws Exception{
  File dir=new File(c.getFilesDir(),"document-diego-models");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("mkdir models");File f=new File(dir,name);
  if(f.isFile()&&sha(f).equals(expected))return f;
  File temp=new File(dir,name+".tmp");try(InputStream in=c.getAssets().open(name);OutputStream out=new FileOutputStream(temp)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
  if(!sha(temp).equals(expected))throw new IOException("SHA "+name);java.nio.file.Files.move(temp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);return f;
 }
 JSONObject identify(Bitmap image)throws Exception{long t=System.nanoTime();int w=image.getWidth(),h=image.getHeight();int[] p=new int[w*h];image.getPixels(p,0,w,0,0,w,h);float[] z=FullCatalog.encode(env,encoder,p,w,h,false,false);double encodeMs=CaptureEngine.ms(t);t=System.nanoTime();float[] q=new float[z.length*2];System.arraycopy(z,0,q,0,z.length);System.arraycopy(z,0,q,z.length,z.length);JSONArray top=new JSONArray();
  try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(q),new long[]{2,z.length});OrtSession.Result r=search.run(Collections.singletonMap("query",x))){FloatBuffer scores=((OnnxTensor)r.get(0)).getFloatBuffer();LongBuffer ix=((OnnxTensor)r.get(1)).getLongBuffer();for(int i=0;i<5&&ix.hasRemaining();i++){int index=(int)ix.get();top.put(new JSONObject().put("id",ids.getString(index)).put("label",labels.getString(index)).put("cosine",scores.get()));}}
  return new JSONObject().put("model","diego").put("orientation","upright-only").put("encoder_passes",1).put("encode_ms",encodeMs).put("search_ms",CaptureEngine.ms(t)).put("top",top);
 }
 public void close(){if(search!=null)try{search.close();}catch(Exception ignored){}if(encoder!=null)try{encoder.close();}catch(Exception ignored){}search=null;encoder=null;}
}
