package io.asv.odinlab;



import android.Manifest;

import android.content.pm.PackageManager;

import android.graphics.*;

import android.os.*;

import android.view.*;

import android.widget.*;

import androidx.activity.ComponentActivity;

import androidx.camera.core.*;

import androidx.camera.lifecycle.ProcessCameraProvider;

import androidx.camera.view.PreviewView;

import androidx.camera.view.transform.*;

import androidx.core.content.ContextCompat;

import java.io.*;

import java.util.*;

import java.util.concurrent.*;

import java.util.concurrent.atomic.*;

import org.json.*;



/** CameraX experiment only. No collection dependencies, background capture or network. */

@android.annotation.SuppressLint("UnsafeOptInUsageError")

public class LiveCameraActivity extends ComponentActivity {

 private record Config(int combination,boolean all,boolean detectOnly,float x,float y,long generation,long epoch){}

 private volatile Config config=new Config(0,false,false,.5f,.5f,0,SystemClock.elapsedRealtimeNanos());

 private final ScanSoundGate soundGate=new ScanSoundGate();private android.media.ToneGenerator scanTone;private boolean soundEnabled=true;
 private final RgbaFrameConverter converter=new RgbaFrameConverter();private boolean conversionVerified;
 private volatile int resolutionProfile; private boolean probeScheduled;

 private volatile boolean active,ready,paused;private LiveEngine engine;private ProcessCameraProvider provider;private ImageAnalysis analysis;

 private final ExecutorService worker=Executors.newSingleThreadExecutor(),disk=Executors.newSingleThreadExecutor();private final AtomicBoolean evidence=new AtomicBoolean();private final StableCrop stable=new StableCrop();

 private PreviewView preview;private Overlay overlay;private TextView status;private ImageView cropView;private Bitmap shownCrop;private File sessionDir;private BufferedWriter log;private int logged;private long frameNumber,lastGeneration=-1;private double firstCrop=-1,stableCrop=-1;private final ArrayList<Double> detectSamples=new ArrayList<>(),totalSamples=new ArrayList<>();

 @Override public void onCreate(Bundle state){super.onCreate(state);LiveEngine.COMBINATIONS[12]=getString(R.string.lcnet_milo);LiveEngine.COMBINATIONS[13]=getString(R.string.lcnet_diego);LiveEngine.COMBINATIONS[14]=getString(R.string.mlbox_milo);LiveEngine.COMBINATIONS[15]=getString(R.string.mlbox_diego);LiveEngine.COMBINATIONS[16]=getString(R.string.mlref_milo);LiveEngine.COMBINATIONS[17]=getString(R.string.mlref_diego);LiveEngine.COMBINATIONS[18]=getString(R.string.rift_milo);LiveEngine.COMBINATIONS[19]=getString(R.string.rift_diego);LiveEngine.COMBINATIONS[20]=getString(R.string.fastweb_milo);LiveEngine.COMBINATIONS[21]=getString(R.string.fastweb_diego);resolutionProfile=getIntent().getIntExtra("cameraProfile",0);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

  LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);column.setPadding(12,24,12,12);TextView title=new TextView(this);title.setText("CameraX LAB · codex/dinov2-benchmark\nSolo vertical, título arriba · una inferencia");column.addView(title);

  Spinner resolution=new Spinner(this);resolution.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.camera_profiles)));resolution.setSelection(resolutionProfile);column.addView(resolution);resolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){} public void onItemSelected(AdapterView<?> a,View v,int pos,long id){if(resolutionProfile!=pos){resolutionProfile=pos;change(config.combination,config.all,config.detectOnly,config.x,config.y);if(ready)ensureCamera();}}});

  Spinner combo=new Spinner(this);combo.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,LiveEngine.COMBINATIONS));combo.setSelection(Math.max(0,Math.min(LiveEngine.COMBINATIONS.length-1,getIntent().getIntExtra("cameraCombination",0))));column.addView(combo);LinearLayout switches=new LinearLayout(this);Switch all=new Switch(this),detectOnly=new Switch(this);all.setText("Tm completo (más lento)");detectOnly.setText("Solo recorte");switches.addView(all);switches.addView(detectOnly);column.addView(switches);Switch sound=new Switch(this);sound.setText("Sonido: candidato estable (no confirma acierto)");sound.setChecked(true);column.addView(sound);sound.setOnCheckedChangeListener((v,on)->{soundEnabled=on;soundGate.reset();});try{scanTone=new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,70);}catch(RuntimeException ignored){}

  combo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){change(pos,all.isChecked(),detectOnly.isChecked(),.5f,.5f);}public void onNothingSelected(AdapterView<?> p){}});all.setOnCheckedChangeListener((v,c)->change(combo.getSelectedItemPosition(),c,detectOnly.isChecked(),config.x,config.y));detectOnly.setOnCheckedChangeListener((v,c)->change(combo.getSelectedItemPosition(),all.isChecked(),c,config.x,config.y));

  android.widget.FrameLayout panel=new android.widget.FrameLayout(this);preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);overlay=new Overlay();panel.addView(preview,new android.widget.FrameLayout.LayoutParams(-1,-1));panel.addView(overlay,new android.widget.FrameLayout.LayoutParams(-1,-1));column.addView(panel,new LinearLayout.LayoutParams(-1,0,1));

  LinearLayout row=new LinearLayout(this);Button pause=new Button(this),save=new Button(this),measure=new Button(this);pause.setText("Pausar");save.setText(R.string.ab22);measure.setText("Nueva medición");row.addView(pause,new LinearLayout.LayoutParams(0,-2,1));row.addView(save,new LinearLayout.LayoutParams(0,-2,1));row.addView(measure,new LinearLayout.LayoutParams(0,-2,1));column.addView(row);

  pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Continuar":"Pausar");change(config.combination,config.all,config.detectOnly,config.x,config.y);});measure.setOnClickListener(v->change(config.combination,config.all,config.detectOnly,.5f,.5f));save.setOnClickListener(v->{if(ready&&active&&!paused){evidence.set(true);Toast.makeText(this,R.string.ab22_notice,Toast.LENGTH_LONG).show();}});

  LinearLayout bottom=new LinearLayout(this);cropView=new TimedCrop();cropView.setScaleType(ImageView.ScaleType.FIT_CENTER);bottom.addView(cropView,new LinearLayout.LayoutParams(110,154));status=new TextView(this);status.setTextSize(13);status.setText("Cargando modelos una sola vez…\nToca una carta para elegirla; verde=seleccionada.\nTm gris + filtro de marco por defecto.");bottom.addView(status,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));column.addView(bottom);setContentView(column);

  try{sessionDir=new File(getFilesDir(),"live/"+System.currentTimeMillis());if(!sessionDir.mkdirs())throw new IOException("mkdir");log=new BufferedWriter(new FileWriter(new File(sessionDir,"frames.jsonl")));}catch(Exception e){status.setText(e.toString());return;}

  worker.execute(()->{try{engine=new LiveEngine(getApplicationContext());JSONObject manifest=new JSONObject().put("fastweb_model",FullCatalog.json(this,"fastweb-model.json")).put("pose_models",engine.poseSpec).put("catalog",engine.spec.getString("catalog_version")).put("models",engine.spec.getJSONObject("models")).put("orientation","upright-only; sensor rotation correction is not card rotation").put("encoder_passes_per_selected_card",1).put("policy","CameraX KEEP_ONLY_LATEST; select nearest target; Tmikonen reject-frame; no frame backlog; no automatic image storage").put("load_ms",engine.loadMs);CaptureEngine.save(sessionDir,"manifest.json",manifest);

    // Explicit warmup, excluded from live measurements.

    Bitmap b;try(InputStream in=getAssets().open("q0.png")){b=BitmapFactory.decodeStream(in);}try{engine.identify(b,true);engine.identify(b,false);engine.detect(b,false,false);engine.detect(b,true,false);engine.detect(b,2,false);engine.detect(b,3,false);engine.detect(b,4,false);engine.detect(b,5,false);}finally{b.recycle();}

    ready=true;runOnUiThread(()->{if(!isDestroyed()){status.setText("Listo. Concede permiso de cámara SOLO a Diego Lab.\nToca una carta; solo se identifica la seleccionada.");ensureCamera();}});

   }catch(Exception e){runOnUiThread(()->status.setText("Error de laboratorio: "+e));}});

 }

 private void change(int mode,boolean all,boolean only,float x,float y){soundGate.reset();Config old=config;config=new Config(mode,all,only,x,y,old.generation+1,SystemClock.elapsedRealtimeNanos());detectSamples.clear();totalSamples.clear();if(overlay!=null)overlay.clear();if(status!=null)status.setText(paused?"Pausado. Cámara sin análisis.":"Nueva configuración · esperando frame. No se conserva un recorte viejo.");if(shownCrop!=null){cropView.setImageDrawable(null);shownCrop.recycle();shownCrop=null;}}

 @Override protected void onResume(){super.onResume();active=true;if(ready)ensureCamera();}

 @Override protected void onPause(){active=false;if(scanTone!=null)scanTone.stopTone();change(config.combination,config.all,config.detectOnly,config.x,config.y);evidence.set(false);if(analysis!=null)analysis.clearAnalyzer();if(provider!=null)provider.unbindAll();super.onPause();}

 private void ensureCamera(){if(!active||!ready||isDestroyed())return;if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},31);return;}preview.post(this::bindCamera);}

 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==31){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)ensureCamera();else status.setText("Sin permiso de cámara. Vuelve a abrir CameraX para solicitarlo; no se ha iniciado ninguna captura.");}}

 @android.annotation.SuppressLint("MissingPermission")

 private void bindCamera(){if(!active||!ready||isDestroyed()||ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;

  var future=ProcessCameraProvider.getInstance(this);future.addListener(()->{try{if(!active||isDestroyed())return;provider=future.get();provider.unbindAll();Preview.Builder pb=new Preview.Builder().setTargetRotation(Surface.ROTATION_0);ImageAnalysis.Builder ab=new ImageAnalysis.Builder().setTargetRotation(Surface.ROTATION_0).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
if(resolutionProfile>=2&&resolutionProfile<=4){final int cap=resolutionProfile==4?960:1280;var selector=new androidx.camera.core.resolutionselector.ResolutionSelector.Builder().setResolutionFilter((sizes,rotation)->{java.util.List<android.util.Size> chosen=new java.util.ArrayList<>();for(var size:sizes)if(Math.max(size.getWidth(),size.getHeight())<=cap)chosen.add(size);chosen.sort((a,b)->Long.compare((long)b.getWidth()*b.getHeight(),(long)a.getWidth()*a.getHeight()));return chosen;}).build();ab.setResolutionSelector(selector);pb.setResolutionSelector(selector);}else{ab.setTargetResolution(resolutionProfile==1?new android.util.Size(960,1280):new android.util.Size(1280,960));}
Preview p=pb.build();p.setSurfaceProvider(preview.getSurfaceProvider());analysis=ab.build();analysis.setAnalyzer(worker,this::analyze);ViewPort viewport=preview.getViewPort();if(viewport==null){status.setText("Esperando tamaño del visor");return;}provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,new UseCaseGroup.Builder().setViewPort(viewport).addUseCase(p).addUseCase(analysis).build());}catch(Exception e){status.setText("CameraX: "+e);}},ContextCompat.getMainExecutor(this));

 }

 private void analyze(ImageProxy image){long start=SystemClock.elapsedRealtimeNanos();Config cfg=config;if(!active||paused||!ready){image.close();return;}Bitmap frame=null,crop=null;long number=++frameNumber;int profile=resolutionProfile;AtomicLong drawn=new AtomicLong();

  try{ImageProxyTransformFactory factory=new ImageProxyTransformFactory();factory.setUsingCropRect(true);factory.setUsingRotationDegrees(true);OutputTransform sourceTransform=factory.getOutputTransform(image);boolean pooled=profile>=3;frame=pooled?converter.convert(image):RgbaFrameConverter.legacy(image);double conversion=(SystemClock.elapsedRealtimeNanos()-start)/1e6;long sensorTimestamp=image.getImageInfo().getTimestamp();boolean parityFrame=false;
if(pooled&&!conversionVerified){Bitmap reference=RgbaFrameConverter.legacy(image);try{if(!frame.sameAs(reference))throw new IllegalStateException("RGBA conversion parity failed; do not use this mode");conversionVerified=true;parityFrame=true;CaptureEngine.save(sessionDir,"conversion-parity.json",new JSONObject().put("pixel_exact",true).put("width",frame.getWidth()).put("height",frame.getHeight()).put("rotation",image.getImageInfo().getRotationDegrees()));}finally{reference.recycle();}}


   if(lastGeneration!=cfg.generation){stable.reset();firstCrop=-1;stableCrop=-1;lastGeneration=cfg.generation;}

   LiveEngine.Detection detection=engine.detect(frame,cfg.combination/2,cfg.all);int selected=LiveEngine.select(detection,cfg.x,cfg.y);double elapsed=(SystemClock.elapsedRealtimeNanos()-cfg.epoch)/1e6;if(selected>=0&&firstCrop<0)firstCrop=elapsed;boolean geometryStable=stable.update(selected<0?null:detection.quads.get(selected));if(geometryStable&&stableCrop<0)stableCrop=elapsed;

   int fw=frame.getWidth(),fh=frame.getHeight();

   if(getIntent().getBooleanExtra("cameraProbe",false)&&!probeScheduled){probeScheduled=true;runOnUiThread(()->new Handler(Looper.getMainLooper()).postDelayed(()->{if(!isDestroyed())finish();},25000));}

runOnUiThread(()->{if(active&&!paused&&config.generation==cfg.generation)overlay.show(detection,selected,sourceTransform,fw,fh,start,drawn,number,cfg.generation);});

   JSONObject result=new JSONObject().put("fastweb_sharpness",Double.isFinite(detection.sharpness)?detection.sharpness:JSONObject.NULL).put("frame",number).put("resolution_profile",profile).put("generation",cfg.generation).put("combination",LiveEngine.COMBINATIONS[cfg.combination]).put("tm_mode",cfg.all?"all-reject-frame":"gray-reject-frame").put("only_detect",cfg.detectOnly).put("sensor_timestamp_ns",sensorTimestamp).put("sensor_clock_note","not used for latency: clock alignment not assumed").put("width",fw).put("height",fh).put("conversion_ms",conversion).put("conversion_pooled",pooled).put("conversion_parity_frame",parityFrame).put("detect_ms",detection.ms).put("detector_prepare_ms",detection.prepareMs).put("detector_inference_ms",detection.inferenceMs).put("buffer_width",image.getWidth()).put("buffer_height",image.getHeight()).put("sensor_rotation",image.getImageInfo().getRotationDegrees()).put("crop_rect",image.getCropRect().toShortString()).put("quads",LiveEngine.jsonQuads(detection)).put("rejected",detection.rejected).put("selected",selected).put("manual_start_to_first_crop_ms",firstCrop).put("manual_start_to_stable_crop_ms",stableCrop);

   if(selected>=0){long t=System.nanoTime();crop=LiveEngine.crop(frame,detection.quads.get(selected));result.put("warp_ms",CaptureEngine.ms(t));Bitmap early=crop.copy(Bitmap.Config.ARGB_8888,false);runOnUiThread(()->{if(active&&!paused&&config.generation==cfg.generation){((TimedCrop)cropView).show(early,number,cfg.generation,start);if(shownCrop!=null)shownCrop.recycle();shownCrop=early;}else early.recycle();});if(!cfg.detectOnly)result.put("identification",engine.identify(crop,cfg.combination%2==0));}

   double total=(SystemClock.elapsedRealtimeNanos()-start)/1e6;result.put("analysis_ms",total).put("thermal",((PowerManager)getSystemService(POWER_SERVICE)).getCurrentThermalStatus());Bitmap displayCrop=null;double first=firstCrop,stableMs=stableCrop;

   runOnUiThread(()->{if(!active||paused||config.generation!=cfg.generation){if(displayCrop!=null)displayCrop.recycle();return;}try{long paint=drawn.get();result.put("first_overlay_draw_ms",paint==0?JSONObject.NULL:(paint-start)/1e6).put("ui_result_ms",(SystemClock.elapsedRealtimeNanos()-start)/1e6);detectSamples.add(detection.ms);totalSamples.add(total);if(detectSamples.size()>100){detectSamples.remove(0);totalSamples.remove(0);}String label=result.has("identification")?result.getJSONObject("identification").getJSONArray("top").getJSONObject(0).getString("label"):"Sin identificación: selecciona una carta vertical";

     String soundId=result.has("identification")?result.getJSONObject("identification").getJSONArray("top").getJSONObject(0).getString("id"):null;boolean beep=soundEnabled&&soundGate.update(soundId,geometryStable,SystemClock.elapsedRealtime());result.put("sound_candidate",beep);if(beep&&scanTone!=null)scanTone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,120);
     status.setText(String.format(Locale.ROOT,"%s\n%d propuestas · recorte %.0f ms · total %.0f ms\np50 recorte/total %.0f / %.0f ms (n=%d)\nDesde Nueva medición: primera %.0f ms · estable %.0f ms\n%s\nToca otra carta. Similitud ≠ acierto.",LiveEngine.COMBINATIONS[cfg.combination],detection.quads.size(),detection.ms,total,median(detectSamples),median(totalSamples),detectSamples.size(),first,stableMs,label));if(selected<0&&shownCrop!=null){cropView.setImageDrawable(null);shownCrop.recycle();shownCrop=null;}String line=result.toString();disk.execute(()->writeLog(line));

    }catch(Exception e){status.setText(e.toString());if(displayCrop!=null&&shownCrop!=displayCrop)displayCrop.recycle();}});

   if(getIntent().getBooleanExtra("cameraProbe",false)&&number==12){CaptureEngine.png(new File(sessionDir,"probe-frame.png"),frame);CaptureEngine.save(sessionDir,"probe-frame.json",result);}

   if(active&&config.generation==cfg.generation&&evidence.getAndSet(false))saveEvidence(frame,cfg,number);

  }catch(Exception e){String error=e.toString();runOnUiThread(()->{if(active&&config.generation==cfg.generation){overlay.clear();status.setText("Análisis fallido (no cuenta como éxito): "+error);}});disk.execute(()->writeLog("{\"error\":"+JSONObject.quote(error)+",\"frame\":"+number+"}"));}

  finally{if(frame!=null)frame.recycle();if(crop!=null)crop.recycle();image.close();}

 }

 private void saveEvidence(Bitmap frame,Config cfg,long number)throws Exception{File dir=new File(sessionDir,"ab-"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("mkdir");CaptureEngine.png(new File(dir,"frame.png"),frame);JSONObject report=new JSONObject().put("frame",number).put("fastweb_model",FullCatalog.json(this,"fastweb-model.json")).put("pose_models",engine.poseSpec).put("catalog",engine.spec.getString("catalog_version")).put("orientation","upright-only").put("target_x",cfg.x).put("target_y",cfg.y).put("note","same frozen image; detector reused for both identifiers; not live latency; no automatic accuracy judgement");JSONArray rows=new JSONArray();

  for(int detector=0;detector<LiveEngine.DETECTORS.length;detector++){LiveEngine.Detection d=engine.detect(frame,detector,cfg.all);int sel=LiveEngine.select(d,cfg.x,cfg.y);JSONObject det=new JSONObject().put("fastweb_sharpness",Double.isFinite(d.sharpness)?d.sharpness:JSONObject.NULL).put("detector",LiveEngine.DETECTORS[detector]).put("tm_mode",cfg.all?"all":"gray").put("detect_ms",d.ms).put("quads",LiveEngine.jsonQuads(d)).put("selected",sel);report.put(LiveEngine.DETECTORS[detector],det);DetectorComparison.overlay(dir,LiveEngine.DETECTORS[detector]+"-overlay",frame,LiveEngine.jsonQuads(d));if(sel<0)continue;Bitmap c=LiveEngine.crop(frame,d.quads.get(sel));try{CaptureEngine.png(new File(dir,LiveEngine.DETECTORS[detector]+"-crop.png"),c);for(boolean milo:new boolean[]{true,false})rows.put(engine.identify(c,milo).put("detector",LiveEngine.DETECTORS[detector]).put("detect_ms",d.ms));}finally{c.recycle();}}

  report.put("rows",rows);CaptureEngine.save(dir,"comparison.json",report);runOnUiThread(()->{if(!isDestroyed())Toast.makeText(this,"A/B guardado: "+dir.getName(),Toast.LENGTH_LONG).show();});

 }

 private static double median(List<Double> values){List<Double> copy=new ArrayList<>(values);Collections.sort(copy);return copy.get(copy.size()/2);}

 private void writeLog(String line){try{if(logged++<10000){log.write(line);log.newLine();log.flush();}}catch(Exception ignored){}}

 @Override protected void onDestroy(){active=false;if(scanTone!=null){scanTone.release();scanTone=null;}if(analysis!=null)analysis.clearAnalyzer();if(provider!=null)provider.unbindAll();worker.execute(()->{converter.close();if(engine!=null)engine.close();disk.execute(()->{try{if(log!=null)log.close();}catch(Exception ignored){}});disk.shutdown();});worker.shutdown();if(shownCrop!=null){cropView.setImageDrawable(null);shownCrop.recycle();shownCrop=null;}super.onDestroy();}

 private void drawEvent(String type,long frame,long generation,long start){try{String line=new JSONObject().put("event",type).put("frame",frame).put("generation",generation).put("analyzer_to_draw_ms",(SystemClock.elapsedRealtimeNanos()-start)/1e6).put("timing_scope","analyzer callback to onDraw, not sensor exposure or physical display scanout").toString();if(!disk.isShutdown())disk.execute(()->writeLog(line));}catch(Exception ignored){}}

 // Native platform theme: AppCompat is only a transitive ML Kit dependency.
 @android.annotation.SuppressLint("AppCompatCustomView")
 private final class TimedCrop extends ImageView {

  private long frame,generation,start;private boolean pending;

  TimedCrop(){super(LiveCameraActivity.this);}

  void show(Bitmap image,long f,long g,long t){frame=f;generation=g;start=t;pending=true;setImageBitmap(image);}

  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(pending&&active&&!paused&&generation==config.generation&&getDrawable()!=null){pending=false;drawEvent("crop_draw",frame,generation,start);}}

 }

 private final class Overlay extends View {

  private final Paint paint=new Paint();private final List<float[]> polygons=new ArrayList<>();private int selected;private Matrix inverse;private int width,height;private AtomicLong drawn;private long generation,frame,start;

  Overlay(){super(LiveCameraActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}

  void clear(){polygons.clear();inverse=null;invalidate();}

  void show(LiveEngine.Detection d,int sel,OutputTransform source,int w,int h,long start,AtomicLong draw,long frameId,long gen){OutputTransform target=preview.getOutputTransform();if(target==null){clear();return;}Matrix matrix=new Matrix();new CoordinateTransform(source,target).transform(matrix);inverse=new Matrix();if(!matrix.invert(inverse)){clear();return;}polygons.clear();for(double[][] q:d.quads){float[] p=new float[8];for(int i=0;i<4;i++){p[2*i]=(float)q[i][0]*w;p[2*i+1]=(float)q[i][1]*h;}matrix.mapPoints(p);polygons.add(p);}selected=sel;width=w;height=h;drawn=draw;generation=gen;frame=frameId;this.start=start;invalidate();}

  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(generation!=config.generation)return;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);for(int i=0;i<polygons.size();i++){float[] p=polygons.get(i);paint.setColor(i==selected?Color.GREEN:Color.YELLOW);Path path=new Path();path.moveTo(p[0],p[1]);for(int j=1;j<4;j++)path.lineTo(p[j*2],p[j*2+1]);path.close();canvas.drawPath(path,paint);}if(selected>=0&&drawn!=null&&drawn.compareAndSet(0,SystemClock.elapsedRealtimeNanos()))drawEvent("overlay_draw",frame,generation,start);}

  @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==android.view.MotionEvent.ACTION_UP){performClick();if(inverse!=null){float[] point={e.getX(),e.getY()};inverse.mapPoints(point);float x=point[0]/width,y=point[1]/height;if(x>=0&&x<=1&&y>=0&&y<=1)change(config.combination,config.all,config.detectOnly,x,y);}return true;}return true;}

  @Override public boolean performClick(){super.performClick();return true;}

 }

}
