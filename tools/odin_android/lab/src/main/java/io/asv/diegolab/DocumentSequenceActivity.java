package io.asv.odinlab;
import android.app.Activity;import android.os.*;import android.content.Intent;import android.graphics.*;import android.net.Uri;import android.media.*;import android.widget.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import org.json.*;import com.google.mlkit.vision.documentscanner.*;
/** Independent ML Kit UI -> Diego session, never production. */
public class DocumentSequenceActivity extends Activity {
 private TextView status,history;private ImageView preview;private Button single,batch;private Switch sound;private Bitmap shown;private ToneGenerator tone;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private final DocumentSequencePolicy gate=new DocumentSequencePolicy();
 private final Handler feedback=new Handler(Looper.getMainLooper());private long nextToneAt;
 private DocumentDiegoEngine engine;private File session;private String request;private long began;private boolean busy,pending;private volatile boolean alive=true;private int count;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  String id=state==null?"mlkit-sequence-"+UUID.randomUUID():state.getString("session");session=new File(getFilesDir(),id);
  if(state!=null){request=state.getString("request");began=state.getLong("began");pending=state.getBoolean("pending");count=state.getInt("count");}busy=pending;
  LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(24,32,24,24);
  TextView title=new TextView(this);title.setText(R.string.docseq_title);col.addView(title);TextView intro=new TextView(this);intro.setText(R.string.docseq_intro);col.addView(intro);
  single=new Button(this);single.setText(R.string.docseq_single);col.addView(single);batch=new Button(this);batch.setText(R.string.docseq_batch);col.addView(batch);
  sound=new Switch(this);sound.setText(R.string.docseq_sound);sound.setChecked(state==null||state.getBoolean("sound",true));col.addView(sound);
  status=new TextView(this);status.setText(getString(R.string.docseq_ready,session.getName()));col.addView(status);history=new TextView(this);col.addView(history);
  preview=new ImageView(this);preview.setAdjustViewBounds(true);col.addView(preview);ScrollView scroll=new ScrollView(this);scroll.addView(col);setContentView(scroll);
  try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,70);}catch(RuntimeException ignored){}
  enable(!busy);single.setOnClickListener(v->launch(1));batch.setOnClickListener(v->launch(20));
  if(state==null&&getIntent().getBooleanExtra("smoke",false)){busy=true;enable(false);request=UUID.randomUUID().toString();began=SystemClock.elapsedRealtime();process(Arrays.asList(Uri.EMPTY,Uri.EMPTY),true);}
 }
 private void enable(boolean on){single.setEnabled(on);batch.setEnabled(on);}
 private void launch(int pages){if(busy)return;busy=true;enable(false);request=UUID.randomUUID().toString();began=SystemClock.elapsedRealtime();status.setText(R.string.docseq_preparing);
  GmsDocumentScannerOptions options=new GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(pages).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build();
  GmsDocumentScanning.getClient(options).getStartScanIntent(this).addOnSuccessListener(sender->{if(!alive||isFinishing())return;try{pending=true;startIntentSenderForResult(sender,73,null,0,0,0);}catch(Exception e){failed(e);}}).addOnFailureListener(e->{if(alive)failed(e);});
 }
 private void failed(Exception e){if(!alive)return;pending=false;busy=false;enable(true);status.setText(getString(R.string.docseq_error,e.toString()));}
 @Override protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(req!=73||!pending)return;pending=false;
  if(code!=RESULT_OK){busy=false;enable(true);status.setText(R.string.docseq_cancel);return;}
  GmsDocumentScanningResult result=GmsDocumentScanningResult.fromActivityResultIntent(data);
  if(result==null||result.getPages()==null||result.getPages().isEmpty()){failed(new IOException("No JPEG returned"));return;}
  List<Uri> uris=new ArrayList<>();for(GmsDocumentScanningResult.Page p:result.getPages())uris.add(p.getImageUri());process(uris,false);
 }
 private void process(List<Uri> uris,boolean smoke){final String capture=request;final long flow=SystemClock.elapsedRealtime()-began;status.setText(R.string.docseq_identifying);
  worker.execute(()->{int errors=0;try{
   if(!session.isDirectory()&&!session.mkdirs())throw new IOException("mkdir session");
   for(int i=0;i<uris.size();i++){final int page=i;File dir=new File(session,capture+"-"+i);Bitmap image=null;
    if(dir.exists()){errors++;continue;}if(!dir.mkdirs())throw new IOException("mkdir evidence");
    JSONObject record=new JSONObject().put("request",capture).put("page_index",i).put("pages_returned",uris.size()).put("status","pending").put("smoke",smoke).put("workflow_ms_includes_human",flow).put("source","ML Kit BASE corrected JPEG, original camera frame unavailable").put("collection_write",false);
    CaptureEngine.save(dir,"result.json",record);
    try{long t=System.nanoTime();File original=new File(dir,smoke?"document.png":"document.jpg");
     try(InputStream in=smoke?getAssets().open("raw0.png"):getContentResolver().openInputStream(uris.get(i))){if(in==null)throw new IOException("Input unavailable");java.nio.file.Files.copy(in,original.toPath());}
     record.put("source_file",original.getName()).put("source_sha256",DocumentDiegoEngine.sha(original));
     BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(original.toString(),opts);opts.inSampleSize=DocumentSequencePolicy.sampleSize(opts.outWidth,opts.outHeight);opts.inJustDecodeBounds=false;image=BitmapFactory.decodeFile(original.toString(),opts);if(image==null)throw new IOException("JPEG decode");
     record.put("decode_ms",CaptureEngine.ms(t)).put("width",image.getWidth()).put("height",image.getHeight());
     CaptureEngine.png(new File(dir,"input.png"),image);record.put("input_sha256",DocumentDiegoEngine.sha(new File(dir,"input.png")));
     if(engine==null)engine=new DocumentDiegoEngine(getApplicationContext());
     record.put("models",engine.spec.getJSONObject("models")).put("catalog",engine.spec.getString("catalog_version")).put("engine_initial_load_ms",engine.loadMs);
     long identifyStart=System.nanoTime();JSONObject identification=engine.identify(image);record.put("identification",identification).put("identify_ms",CaptureEngine.ms(identifyStart)).put("status","candidate").put("note","Top-1 candidate only; no calibrated acceptance threshold or collection insertion.");
     CaptureEngine.save(dir,"result.json",record);
     JSONArray top=identification.getJSONArray("top");String label=top.length()>0?top.getJSONObject(0).getString("label"):getString(R.string.docseq_no_candidate);
     boolean notify=gate.notifyCandidate(capture+"-"+i,true,top.length()>0);
     double scale=Math.min(320.0/image.getWidth(),448.0/image.getHeight());Bitmap thumb=Bitmap.createScaledBitmap(image,Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),true);if(thumb==image)thumb=image.copy(Bitmap.Config.ARGB_8888,false);
     final Bitmap show=thumb;runOnUiThread(()->{if(!alive){show.recycle();return;}count++;history.append(getString(R.string.docseq_row,count,label));if(shown!=null)shown.recycle();shown=show;preview.setImageBitmap(show);if(notify&&sound.isChecked()){long at=Math.max(SystemClock.uptimeMillis(),nextToneAt);nextToneAt=at+250;feedback.postAtTime(()->{if(alive&&sound.isChecked()&&tone!=null&&!isFinishing())tone.startTone(ToneGenerator.TONE_PROP_BEEP,120);},at);}});
    }catch(Exception e){errors++;record.put("status","error").put("error",e.toString());CaptureEngine.save(dir,"result.json",record);}finally{if(image!=null)image.recycle();}
   }
   final int failures=errors;CaptureEngine.save(session,capture+"-batch.json",new JSONObject().put("pages",uris.size()).put("errors",errors).put("complete",true).put("smoke",smoke));
   runOnUiThread(()->{if(!alive)return;busy=false;enable(true);status.setText(getString(R.string.docseq_finished,uris.size()-failures,failures,session.getName()));});
  }catch(Exception e){runOnUiThread(()->failed(e));}});
 }
 @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("session",session.getName());s.putString("request",request);s.putLong("began",began);s.putBoolean("pending",pending);s.putInt("count",count);s.putBoolean("sound",sound.isChecked());}
 @Override protected void onDestroy(){alive=false;feedback.removeCallbacksAndMessages(null);worker.execute(()->{if(engine!=null)engine.close();});worker.shutdown();if(tone!=null){tone.release();tone=null;}if(shown!=null){preview.setImageDrawable(null);shown.recycle();shown=null;}super.onDestroy();}
}
