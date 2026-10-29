package io.asv.odinlab;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
import ai.onnxruntime.*;

final class Benchmark {
    static final String[] MODES={"diego_fp32","milo_full"};
    static final String[] FIELDS={"illustration_id","set","printing","lang"};
    private final Context context;
    private final Consumer<String> progress;
    private final OrtEnvironment ort=OrtEnvironment.getEnvironment();
    private final Map<String,OrtSession> sessions=new HashMap<>();
    private final Map<String,float[]> galleries=new HashMap<>();
    private final JSONObject results=new JSONObject(), times=new JSONObject();
    private JSONArray queries,refs;
    private double minCosine=1; private final JSONArray parityDetails=new JSONArray();
    private int verifiedViews=0, resizeHashMismatches=0;
    private long peakPssKb=0;
    private File runDir;

    Benchmark(Context context,Consumer<String> progress){this.context=context;this.progress=progress;}
    private byte[] asset(String name)throws Exception{try(InputStream s=context.getAssets().open(name)){return s.readAllBytes();}}
    static String hash(byte[] bytes)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private void save(String name,JSONObject value)throws Exception {
        File temp=new File(runDir,name+".tmp");
        Files.write(temp.toPath(),value.toString(2).getBytes(StandardCharsets.UTF_8));
        if(!temp.renameTo(new File(runDir,name)))throw new IOException("Result rename failed");
    }
    private JSONObject state()throws Exception {
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        long pss=Debug.getPss();peakPssKb=Math.max(peakPssKb,pss);
        return new JSONObject().put("uptime_ms",SystemClock.elapsedRealtime()).put("thermal_status",pm.getCurrentThermalStatus())
            .put("pss_kb",pss).put("battery_temp_c",battery==null?JSONObject.NULL:battery.getIntExtra("temperature",-1)/10.0)
            .put("battery_level",battery==null?-1:battery.getIntExtra("level",-1))
            .put("plugged",battery==null?-1:battery.getIntExtra("plugged",-1));
    }
    private static double ms(long start){return (System.nanoTime()-start)/1e6;}
    private record Encoding(float[][] vectors,byte[][] pixels,double preprocessMs,double inferenceMs){}
    private Encoding encode(String mode,int[] pixels,int w,int h)throws Exception {
        boolean milo=mode.equals("milo_full");int regions=mode.equals("dino_regions")?5:1,size=448;
        OrtSession session=sessions.get(milo?"milo":"diego");
        float[][] out=new float[regions*2][];byte[][] rgbs=new byte[regions*2][];
        double pre=0,infer=0;
        for(int o=0;o<2;o++)for(int r=0;r<regions;r++) {
            int i=o*regions+r;long t=System.nanoTime();
            rgbs[i]=milo?Preprocess.view(pixels,w,h,r,o==1,size,false):Preprocess.diegoView(pixels,w,h,o==1,true);
            float[] input=milo?Preprocess.tensor(rgbs[i]):Preprocess.diegoTensor(rgbs[i]);pre+=ms(t);t=System.nanoTime();
            try(OnnxTensor tensor=OnnxTensor.createTensor(ort,FloatBuffer.wrap(input),new long[]{1,3,448,milo?448:320});
                OrtSession.Result prediction=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),tensor))) {
                FloatBuffer output=((OnnxTensor)prediction.get(0)).getFloatBuffer();
                out[i]=new float[output.remaining()];output.get(out[i]);Ranking.normalize(out[i]);
            }
            infer+=ms(t);
        }
        return new Encoding(out,rgbs,pre,infer);
    }
    private void verify(JSONObject q,String mode,Encoding e)throws Exception {
        String model=mode.equals("milo_full")?"milo":"diego";
        JSONArray hashes=q.getJSONObject("preprocessed_sha256").getJSONArray(model);
        JSONArray expected=q.getJSONObject("expected_embeddings").getJSONArray(model);
        for(int i=0;i<e.vectors.length;i++) {
            int j=mode.equals("dino_full")?i*5:i;
            if(!hash(e.pixels[i]).equals(hashes.getString(j)))
                throw new IllegalStateException("Preprocessed SHA: "+model+" "+q.getString("id"));
            JSONArray v=expected.getJSONArray(j);double dot=0,a=0,b=0;
            if(v.length()!=e.vectors[i].length)throw new IllegalStateException("Embedding shape");
            for(int d=0;d<v.length();d++){double x=e.vectors[i][d],y=v.getDouble(d);dot+=x*y;a+=x*x;b+=y*y;}
            double cos=dot/Math.sqrt(a*b);minCosine=Math.min(minCosine,cos);
            parityDetails.put(new JSONObject().put("id",q.getString("id")).put("mode",mode).put("view",i).put("cosine",cos).put("pixel_sha_matches",hash(e.pixels[i]).equals(hashes.getString(j))));
            if(!Double.isFinite(cos)||cos<.9999)throw new IllegalStateException("ONNX parity: "+cos);
            verifiedViews++;
        }
    }
    private JSONObject row(JSONObject q,Ranking.Result ranked)throws Exception {
        JSONArray top=new JSONArray();JSONObject fields=new JSONObject();
        for(String field:FIELDS){LinkedHashSet<String> distinct=new LinkedHashSet<>();
            for(int index:ranked.indices())distinct.add(refs.getJSONObject(index).getString(field));
            fields.put(field,new JSONArray(distinct));}
        for(int k=0;k<5;k++) {
            int i=ranked.indices()[k];JSONObject r=new JSONObject(refs.getJSONObject(i).toString());
            r.put("score",ranked.scores()[i]).put("rotation",ranked.orientations()[i]);top.put(r);
        }
        return new JSONObject().put("id",q.getString("id")).put("truth",q.getJSONObject("truth"))
            .put("top",top).put("field_ranks",fields);
    }
    private void time(String mode,String stage,double value)throws Exception{times.getJSONObject(mode).getJSONArray(stage).put(value);}
    private boolean covered(JSONObject truth,String field)throws Exception {
        for(int i=0;i<refs.length();i++) {
            JSONObject ref=refs.getJSONObject(i);boolean match=ref.getString(field).equals(truth.optString(field));
            for(String identity:new String[]{"illustration_id","printing"})
                if(!identity.equals(field)&&truth.has(identity))match &=ref.getString(identity).equals(truth.getString(identity));
            if(match)return true;
        }
        return false;
    }
    private static double percentile(JSONArray array,double p)throws Exception {
        double[] values=new double[array.length()];for(int i=0;i<values.length;i++)values[i]=array.getDouble(i);
        Arrays.sort(values);double pos=(values.length-1)*p;int lo=(int)pos,hi=(int)Math.ceil(pos);
        return values[lo]+(values[hi]-values[lo])*(pos-lo);
    }
    private JSONObject summary(String mode)throws Exception {
        JSONArray rows=results.getJSONArray(mode);int art=0,printing=0,lang=0;
        for(int i=0;i<rows.length();i++) {
            JSONObject r=rows.getJSONObject(i),t=r.getJSONObject("truth"),pred=r.getJSONArray("top").getJSONObject(0);
            if(t.optString("illustration_id","?").equals(pred.getString("illustration_id")))art++;
            if(t.optString("printing","?").equals(pred.getString("printing")))printing++;
            if(covered(t,"lang")&&t.optString("lang","?").equals(pred.getString("lang")))lang++;
        }
        JSONArray total=times.getJSONObject(mode).getJSONArray("total");
        return new JSONObject().put("art_correct",art).put("printing_correct",printing).put("language_covered_correct",lang)
            .put("p50_ms",percentile(total,.5)).put("p95_ms",percentile(total,.95));
    }
    private JSONArray detectOriginals(JSONObject corpus)throws Exception {
        JSONArray rows=new JSONArray(), originals=corpus.getJSONArray("originals");OrtSession session=sessions.get("cornelius");
        for(int i=0;i<originals.length();i++) {
            JSONObject row=originals.getJSONObject(i);Bitmap b;try(InputStream stream=context.getAssets().open(row.getString("asset"))){b=BitmapFactory.decodeStream(stream);}
            int w=b.getWidth(),h=b.getHeight();int[] p=new int[w*h];b.getPixels(p,0,w,0,0,w,h);b.recycle();JSONArray elapsed=new JSONArray();float[] quad=null;
            for(int rep=0;rep<5;rep++) {long t=System.nanoTime();float[] input=Preprocess.tensor(Preprocess.view(p,w,h,0,false,384,false));
                try(OnnxTensor x=OnnxTensor.createTensor(ort,FloatBuffer.wrap(input),new long[]{1,3,384,384});OrtSession.Result y=session.run(Collections.singletonMap(session.getInputNames().iterator().next(),x))){
                    FloatBuffer buffer=((OnnxTensor)y.get("corners").get()).getFloatBuffer();quad=new float[buffer.remaining()];buffer.get(quad);
                }double ms=ms(t);if(rep>=2)elapsed.put(ms);
            }
            JSONArray corners=new JSONArray();for(float v:quad)corners.put(v);rows.put(new JSONObject().put("id",row.getString("id")).put("corners",corners).put("total_ms",elapsed));
        }return rows;
    }
    JSONObject run()throws Exception {
        if(!org.opencv.android.OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV init");org.opencv.core.Core.setNumThreads(1);
        runDir=new File(context.getFilesDir(),"run-"+System.currentTimeMillis());
        if(!runDir.mkdirs())throw new IOException("Run folder");
        try {
            JSONObject env=new JSONObject().put("model",Build.MODEL).put("device",Build.DEVICE).put("sdk",Build.VERSION.SDK_INT)
                .put("fingerprint",Build.FINGERPRINT).put("soc",Build.SOC_MODEL).put("ort",ort.getVersion())
                .put("provider","CPUExecutionProvider").put("threads",4).put("repeats",3).put("batch",1)
                .put("branch","codex/dinov2-benchmark").put("run",runDir.getName())
                .put("timing_scope","crop+resize+normalization+ONNX+L2+search; excludes decode, hashes, parity, model/gallery load")
                .put("start_state",state());
            long prepare=System.nanoTime();
            JSONObject checksums=new JSONObject(new String(asset("checksums.json"),StandardCharsets.UTF_8));
            Iterator<String> keys=checksums.keys();while(keys.hasNext()) {
                String name=keys.next();if(!hash(asset(name)).equals(checksums.getString(name)))throw new IOException("Asset SHA: "+name);
            }
            JSONObject corpus=new JSONObject(new String(asset("corpus.json"),StandardCharsets.UTF_8));
            queries=corpus.getJSONArray("queries");refs=corpus.getJSONArray("references");
            if(queries.length()!=49||refs.length()!=490)throw new IllegalStateException("Corpus size");
            env.put("export",corpus.getJSONObject("export")).put("corpus_sha256",checksums.getString("corpus.json"));
            for(String model:new String[]{"diego","milo","cornelius"}) {
                File local=new File(context.getFilesDir(),model+".onnx");
                if(!local.exists()||!hash(Files.readAllBytes(local.toPath())).equals(checksums.getString(model+".onnx")))
                    Files.write(local.toPath(),asset(model+".onnx"));
                try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
                    options.setIntraOpNumThreads(4);options.setInterOpNumThreads(1);
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                    long t=System.nanoTime();sessions.put(model,ort.createSession(local.toString(),options));env.put(model+"_load_ms",ms(t));
                }
            }
            for(String mode:MODES){byte[] bytes=asset(mode+".bin");float[] g=new float[bytes.length/4];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(g);galleries.put(mode,g);
                results.put(mode,new JSONArray());JSONObject stages=new JSONObject();
                for(String stage:new String[]{"preprocess","inference","encode","search","total"})stages.put(stage,new JSONArray());
                times.put(mode,stages);}
            env.put("preparation_ms",ms(prepare));
            JSONArray telemetry=new JSONArray();
            for(int qi=0;qi<queries.length();qi++) {
                JSONObject q=queries.getJSONObject(qi);Bitmap bitmap;
                try(InputStream s=context.getAssets().open(q.getString("asset"))){bitmap=BitmapFactory.decodeStream(s);}
                if(bitmap==null)throw new IOException("Decode "+qi);
                int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);bitmap.recycle();
                if(qi==0)for(String mode:MODES)for(int warm=0;warm<2;warm++) {
                    Encoding e=encode(mode,pixels,w,h);int r=mode.equals("dino_regions")?5:1;
                    Ranking.rank(galleries.get(mode),refs.length(),r,e.vectors[0].length,e.vectors);
                }
                for(int rep=0;rep<3;rep++)for(int index=0;index<MODES.length;index++) {
                    String mode=MODES[(index+qi+rep)%MODES.length];progress.accept(context.getString(R.string.progress,qi+1,mode));
                    long t=System.nanoTime();Encoding e=encode(mode,pixels,w,h);double enc=ms(t);t=System.nanoTime();
                    Ranking.Result ranked=Ranking.rank(galleries.get(mode),refs.length(),mode.equals("dino_regions")?5:1,e.vectors[0].length,e.vectors);
                    double search=ms(t);
                    time(mode,"preprocess",e.preprocessMs);time(mode,"inference",e.inferenceMs);
                    time(mode,"encode",enc);time(mode,"search",search);time(mode,"total",enc+search);
                    if(rep==0){verify(q,mode,e);results.getJSONArray(mode).put(row(q,ranked));}
                }
                telemetry.put(state().put("query",qi));
                save("partial.json",new JSONObject().put("completed_queries",qi+1).put("results",results).put("raw_times_ms",times));
            }
            env.put("end_state",state()).put("peak_sampled_pss_kb",peakPssKb).put("verified_views",verifiedViews)
                .put("parity_details",parityDetails).put("resize_hash_mismatches",resizeHashMismatches).put("min_embedding_cosine_pc",minCosine).put("telemetry",telemetry);
            JSONArray detections=detectOriginals(corpus);
            JSONObject summaries=new JSONObject();for(String mode:MODES)summaries.put(mode,summary(mode));
            JSONObject report=new JSONObject().put("environment",env).put("results",results).put("raw_times_ms",times).put("device_summaries",summaries).put("cornelius",detections);
            save("results.json",report);
            Files.write(new File(context.getFilesDir(),"latest.json").toPath(),report.toString(2).getBytes(StandardCharsets.UTF_8));
            return report;
        } catch(Exception e){save("error.json",new JSONObject().put("error",e.toString()));throw e;}
        finally {for(OrtSession s:sessions.values())s.close();sessions.clear();galleries.clear();}
    }
}
