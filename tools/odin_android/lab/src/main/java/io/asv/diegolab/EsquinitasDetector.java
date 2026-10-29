package io.asv.odinlab;
import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.*;
import java.nio.*;
import java.util.*;
import org.json.*;
final class EsquinitasDetector implements AutoCloseable {
 final OrtEnvironment env=OrtEnvironment.getEnvironment();final OrtSession model;float[] lastInput;
 EsquinitasDetector(Context c)throws Exception {try(OrtSession.SessionOptions o=new OrtSession.SessionOptions()){o.setIntraOpNumThreads(4);o.setInterOpNumThreads(1);model=env.createSession(FullCatalog.model(c,"esquinitas.onnx",FullCatalog.json(c,"esquinitas-model.json").getString("esquinitas.onnx")).toString(),o);}}
 JSONObject run(Bitmap frame)throws Exception {int w=frame.getWidth(),h=frame.getHeight();int[] p=new int[w*h];frame.getPixels(p,0,w,0,0,w,h);byte[] rgb=new byte[p.length*3];for(int i=0;i<p.length;i++){rgb[i*3]=(byte)(p[i]>>16);rgb[i*3+1]=(byte)(p[i]>>8);rgb[i*3+2]=(byte)p[i];}rgb=OwnIdentityPreprocess.resize(rgb,w,h,160,224,false);int n=160*224;lastInput=new float[n*3];for(int c=0;c<3;c++)for(int i=0;i<n;i++)lastInput[c*n+i]=(rgb[i*3+c]&255)/255f;
  try(OnnxTensor x=OnnxTensor.createTensor(env,FloatBuffer.wrap(lastInput),new long[]{1,3,224,160});OrtSession.Result r=model.run(Map.of("image",x))){FloatBuffer f=((OnnxTensor)r.get(0)).getFloatBuffer();JSONArray q=new JSONArray();while(f.hasRemaining())q.put(f.get());float score=((OnnxTensor)r.get(1)).getFloatBuffer().get();return new JSONObject().put("corners",q).put("presence",score).put("experimental",true);}}
 public void close(){try{model.close();}catch(Exception ignored){}}
}
