package io.asv.odinlab;
import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.*;
import org.json.*;
import java.nio.*;
import java.util.*;
final class OdinIdentifier implements AutoCloseable {
 final OrtEnvironment env=OrtEnvironment.getEnvironment();final OrtSession encoder,search;final JSONArray ids;final Map<String,String> names=new HashMap<>();final float[] bias;float[] lastTensor,lastEmbedding;
 OdinIdentifier(Context c)throws Exception {
  JSONObject manifest=FullCatalog.json(c,"ownidentity/manifest.json");ids=CaptureEngine.array(c,"ownidentity/ids.json");bias=new float[ids.length()];
  JSONArray old=CaptureEngine.array(c,"full-ids.json"),labels=CaptureEngine.array(c,"scan-labels.json");for(int i=0;i<old.length();i++)names.put(old.getString(i),labels.getString(i));
  try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){o.setIntraOpNumThreads(4);o.setInterOpNumThreads(1);encoder=env.createSession(FullCatalog.model(c,"ownidentity/encoder.onnx",manifest.getString("encoder.onnx")).toString(),o);search=env.createSession(FullCatalog.model(c,"ownidentity/search.onnx",manifest.getString("search.onnx")).toString(),o);}
 }
 JSONObject identify(Bitmap crop)throws Exception {
  long t=System.nanoTime();int w=crop.getWidth(),h=crop.getHeight();int[] pixels=new int[w*h];crop.getPixels(pixels,0,w,0,0,w,h);
  lastTensor=Preprocess.tensor(OwnIdentityPreprocess.view(pixels,w,h));float[] z;
  try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(lastTensor),new long[]{1,3,448,320});OrtSession.Result r=encoder.run(Collections.singletonMap(encoder.getInputNames().iterator().next(),x))){FloatBuffer b=((OnnxTensor)r.get(0)).getFloatBuffer();z=new float[b.remaining()];b.get(z);}
  if(z.length!=256)throw new IllegalStateException("Odin dimension");Ranking.normalize(z);lastEmbedding=z.clone();double encode=CaptureEngine.ms(t);t=System.nanoTime();float[] both=new float[512];System.arraycopy(z,0,both,0,256);System.arraycopy(z,0,both,256,256);JSONArray top=new JSONArray();
  try(OnnxTensor q=OnnxTensor.createTensor(env,FloatBuffer.wrap(both),new long[]{2,256});OnnxTensor b=OnnxTensor.createTensor(env,FloatBuffer.wrap(bias),new long[]{bias.length});OrtSession.Result r=search.run(Map.of("query",q,"scope_bias",b))){FloatBuffer scores=((OnnxTensor)r.get(0)).getFloatBuffer();LongBuffer indices=((OnnxTensor)r.get(1)).getLongBuffer();while(indices.hasRemaining()){String id=ids.getString((int)indices.get());top.put(new JSONObject().put("id",id).put("label",names.getOrDefault(id,id)).put("cosine",scores.get()));}}
  return new JSONObject().put("model","odin-own02").put("gallery_count",ids.length()).put("orientation","upright-only").put("encoder_passes",1).put("encode_ms",encode).put("search_ms",CaptureEngine.ms(t)).put("top",top);
 }
 public void close(){try{encoder.close();search.close();}catch(Exception ignored){}}
}
