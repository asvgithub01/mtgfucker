/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.asv.mtgocr.ocrreader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import io.asv.mtgocr.ocrreader.data.DataProviderBase;
import io.asv.mtgocr.ocrreader.data.IDataProvider;
import io.asv.mtgocr.ocrreader.data.MtgDataProvider;
import io.asv.mtgocr.ocrreader.model.Biblio;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.Deck;
import io.asv.mtgocr.ocrreader.model.Decks;
import io.asv.mtgocr.ocrreader.ui.camera.CameraSource;
import io.asv.mtgocr.ocrreader.ui.camera.CameraSourcePreview;
import io.asv.mtgocr.ocrreader.ui.camera.GraphicOverlay;

import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.wagnerandade.coollection.Coollection.from;

/**
 * Activity for the multi-tracker app.  This app detects text and displays the value with the
 * rear facing camera. During detection overlay graphics are drawn to indicate the position,
 * size, and contents of each TextBlock.
 */
public final class OcrCaptureActivity extends AppCompatActivity implements View.OnClickListener {
  private static final String TAG = "OcrCaptureActivity";

  // Intent request code to handle updating play services if needed.
  private static final int RC_HANDLE_GMS = 9001;

  // Permission request codes need to be < 256
  private static final int RC_HANDLE_CAMERA_PERM = 2;

  private CameraSource mCameraSource;
  private CameraSourcePreview mPreview;
  private GraphicOverlay<OcrGraphic> mGraphicOverlay;

  // Helper objects for detecting taps and pinches.
  private ScaleGestureDetector scaleGestureDetector;
  private GestureDetector gestureDetector;

  String mPersistorMode;
  public static Biblio mBiblio;
  Decks mDecks;
  Deck mDeck;
  private RecyclerView mRecyclerView;
  private RecyclerView.Adapter mAdapter;
  private RecyclerView.LayoutManager mLayoutManager;

  Button btnOk, btnCancel;
  FloatingActionButton fabOcr;
  EditText txtSearch;
  RelativeLayout lytSearch, lytRecycler;
  LinearLayout topLayout;

  /**
   * Initializes the UI and creates the detector pipeline.
   */
  @Override public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.ocr_capture);
    //region asv

    imgBgCard = (ImageView) findViewById(R.id.imgBgCard);
    btnOk = (Button) findViewById(R.id.btnOk);
    btnCancel = (Button) findViewById(R.id.btnCancel);
    fabOcr = (FloatingActionButton) findViewById(R.id.fabOcr);
    txtSearch = (EditText) findViewById(R.id.txtSearch);
    lytSearch = (RelativeLayout) findViewById(R.id.lytSearch);

    lytRecycler = (RelativeLayout) findViewById(R.id.lytRecycler);
    topLayout = (LinearLayout) findViewById(R.id.topLayout);
    btnOk.setOnClickListener(this);
    btnCancel.setOnClickListener(this);
    fabOcr.setOnClickListener(this);
    //mnu1

    //endregion
    mPreview = (CameraSourcePreview) findViewById(R.id.preview);
    mGraphicOverlay = (GraphicOverlay<OcrGraphic>) findViewById(R.id.graphicOverlay);

    // read parameters from the intent used to launch the activity.
    boolean autoFocus = getIntent().getBooleanExtra(App.INTENT_AUTO_FOCUS, false);
    boolean useFlash = getIntent().getBooleanExtra(App.INTENT_USE_FLASH, false);
    mPersistorMode = getIntent().getStringExtra(App.INTENT_PERSISTOR_MODE);
    loadPersistModeDataCardInfo();

    //region RecyclerView

    setUpRecyclerView();
    lytRecycler.setVisibility(View.VISIBLE);
    fabOcr.setVisibility(View.VISIBLE);
    topLayout.setVisibility(View.GONE);
    //endregion
    showPersistorUI();

    //region Check for the camera permission before accessing the camera.  If the
    // permission is not granted yet, request permission.
    int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
    if (rc == PackageManager.PERMISSION_GRANTED) {
      createCameraSource(autoFocus, useFlash);
    } else {
      requestCameraPermission();
    }
    //endregion
    gestureDetector = new GestureDetector(this, new CaptureGestureListener());
    scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
  }

  //region recycler
  private void setUpRecyclerView() {
    mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
    mRecyclerView.setHasFixedSize(true);
    mLayoutManager = new LinearLayoutManager(this);
    mRecyclerView.setLayoutManager(mLayoutManager);

    List<CardInfo> lstGrp = from(mBiblio.cards).orderBy("getName").all();

    showTotalPrice(lstGrp);
    mAdapter = new MyAdapter(lstGrp, this);
    mRecyclerView.setAdapter(mAdapter);
    setUpItemTouchHelper();
    setUpAnimationDecoratorHelper();
    //https://github.com/iPaulPro/Android-ItemTouchHelper-Demo/blob/master/app/src/main/java/co/paulburke/android/itemtouchhelperdemo/RecyclerGridFragment.java
  }

  private void showTotalPrice(List<CardInfo> lstGrp) {
    try {
      float sum = 0.0f;
      for (int i = 0; i < lstGrp.size() - 1; i++) {
        float priceM = 0f;
        try {
          priceM = Float.valueOf(lstGrp.get(i).getPriceM().replace("$", ""));
        } catch (Exception e) {
          priceM = 0f;
        }

        sum = sum + priceM;
      }
      TextView txtTotal = (TextView) findViewById(R.id.txtTotal);
      txtTotal.setText("Total Price: " + sum + "$");
    } catch (NumberFormatException e) {
      e.printStackTrace();
    }
  }

  private void setUpItemTouchHelper() {

    ItemTouchHelper.SimpleCallback simpleItemTouchCallback =
        new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

          // we want to cache these and not allocate
          // anything repeatedly in the onChildDraw method
          Drawable background;
          Drawable xMark;
          int xMarkMargin;
          boolean initiated;

          private void init() {
            background = new ColorDrawable(Color.RED);
            xMark = ContextCompat.getDrawable(OcrCaptureActivity.this, R.drawable.ic_menu_delete);
            xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
            xMarkMargin =
                (int) OcrCaptureActivity.this.getResources().getDimension(R.dimen.ic_clear_margin);
            initiated = true;
          }

          // not important, we don't want drag & drop
          @Override public boolean onMove(RecyclerView recyclerView,
              RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
          }

          @Override
          public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getAdapterPosition();
            MyAdapter testAdapter = (MyAdapter) recyclerView.getAdapter();
            if (testAdapter.isUndoOn() && testAdapter.isPendingRemoval(position)) {
              return 0;
            }
            return super.getSwipeDirs(recyclerView, viewHolder);
          }

          @Override public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
            int swipedPosition = viewHolder.getAdapterPosition();
            MyAdapter adapter = (MyAdapter) mRecyclerView.getAdapter();
            boolean undoOn = adapter.isUndoOn();
            if (undoOn) {
              adapter.pendingRemoval(swipedPosition);
            } else {
              adapter.remove(swipedPosition);
              mBiblio.cards.remove(swipedPosition);
              DataUtils.saveSerializable(OcrCaptureActivity.this, mBiblio, mBiblio.nameFile);
            }
          }

          @Override public void onChildDraw(Canvas c, RecyclerView recyclerView,
              RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
              boolean isCurrentlyActive) {
            View itemView = viewHolder.itemView;

            // not sure why, but this method get's called for viewholder that are already swiped away
            if (viewHolder.getAdapterPosition() == -1) {
              // not interested in those
              return;
            }

            if (!initiated) {
              init();
            }

            // draw red background
            background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(),
                itemView.getRight(), itemView.getBottom());
            background.draw(c);

            // draw x mark
            int itemHeight = itemView.getBottom() - itemView.getTop();
            int intrinsicWidth = xMark.getIntrinsicWidth();
            int intrinsicHeight = xMark.getIntrinsicWidth();

            int xMarkLeft = itemView.getRight() - xMarkMargin - intrinsicWidth;
            int xMarkRight = itemView.getRight() - xMarkMargin;
            int xMarkTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
            int xMarkBottom = xMarkTop + intrinsicHeight;
            xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom);

            xMark.draw(c);

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
          }
        };
    ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
    mItemTouchHelper.attachToRecyclerView(mRecyclerView);
  }

  /**
   * We're gonna setup another ItemDecorator that will draw the red background in the empty space
   * while the items are animating to thier new positions
   * after an item is removed.
   */
  private void setUpAnimationDecoratorHelper() {
    mRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {

      // we want to cache this and not allocate anything repeatedly in the onDraw method
      Drawable background;
      boolean initiated;

      private void init() {
        background = new ColorDrawable(Color.RED);
        initiated = true;
      }

      @Override public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {

        if (!initiated) {
          init();
        }

        // only if animation is in progress
        if (parent.getItemAnimator().isRunning()) {

          // some items might be animating down and some items might be animating up to close the gap left by the removed item
          // this is not exclusive, both movement can be happening at the same time
          // to reproduce this leave just enough items so the first one and the last one would be just a little off screen
          // then remove one from the middle

          // find first child with translationY > 0
          // and last one with translationY < 0
          // we're after a rect that is not covered in recycler-view views at this point in time
          View lastViewComingDown = null;
          View firstViewComingUp = null;

          // this is fixed
          int left = 0;
          int right = parent.getWidth();

          // this we need to find out
          int top = 0;
          int bottom = 0;

          // find relevant translating views
          int childCount = parent.getLayoutManager().getChildCount();
          for (int i = 0; i < childCount; i++) {
            View child = parent.getLayoutManager().getChildAt(i);
            if (child.getTranslationY() < 0) {
              // view is coming down
              lastViewComingDown = child;
            } else if (child.getTranslationY() > 0) {
              // view is coming up
              if (firstViewComingUp == null) {
                firstViewComingUp = child;
              }
            }
          }

          if (lastViewComingDown != null && firstViewComingUp != null) {
            // views are coming down AND going up to fill the void
            top = lastViewComingDown.getBottom() + (int) lastViewComingDown.getTranslationY();
            bottom = firstViewComingUp.getTop() + (int) firstViewComingUp.getTranslationY();
          } else if (lastViewComingDown != null) {
            // views are going down to fill the void
            top = lastViewComingDown.getBottom() + (int) lastViewComingDown.getTranslationY();
            bottom = lastViewComingDown.getBottom();
          } else if (firstViewComingUp != null) {
            // views are coming up to fill the void
            top = firstViewComingUp.getTop();
            bottom = firstViewComingUp.getTop() + (int) firstViewComingUp.getTranslationY();
          }

          background.setBounds(left, top, right, bottom);
          background.draw(c);
        }
        super.onDraw(c, parent, state);
      }
    });
  }

  //endregion
  private void loadPersistModeDataCardInfo() {
    if (mPersistorMode.equals("0"))//biblio
    {
      mBiblio = DataUtils.readSerializable(this, "myBiblio.Json");
      if (mBiblio == null) {
        //todo show dialog for create name of mybiblio
        mBiblio = new Biblio("myBiblio.Json", "Mis Cartukis");
        DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
      }
    }
    if (mPersistorMode.equals("1"))//newdeck
    {
      mDeck = DataUtils.readSerializable(this, "myDeck.Json");
      if (mDeck == null) {
        //todo show dialog for create name of deck
        mDeck = new Deck("myDeck.Json", "MiDeck");
      }

      mDecks = DataUtils.readSerializable(this, mDecks.nameFile);
      if (mDecks == null) {
        mDecks = new Decks();
        mDecks.addDeck(mDeck);
      }
      DataUtils.saveSerializable(this, mDecks, mDecks.nameFile);
    }
    if (mPersistorMode.equals("2"))//editDecks
    {
      mDecks = DataUtils.readSerializable(this, mDecks.nameFile);
      if (mDecks == null) {
        mDecks = new Decks();
        mDecks.addDeck(mDeck);
      }
      DataUtils.saveSerializable(this, mDecks, mDecks.nameFile);
    }
  }

  private void showPersistorUI() {
    if (mPersistorMode.equals("0"))//biblio
    {
      //todo
    }
    if (mPersistorMode == "1")//newdeck
    {
      //todo
    }
    if (mPersistorMode == "2")//editDecks
    {
      //todo
    }
  }
  //region activity

  /**
   * Handles the requesting of the camera permission.  This includes
   * showing a "Snackbar" message of why the permission is needed then
   * sending the request.
   */
  private void requestCameraPermission() {
    Log.w(TAG, "Camera permission is not granted. Requesting permission");

    final String[] permissions = new String[] { Manifest.permission.CAMERA };

    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
      ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
      return;
    }

    final Activity thisActivity = this;

    View.OnClickListener listener = new View.OnClickListener() {
      @Override public void onClick(View view) {
        ActivityCompat.requestPermissions(thisActivity, permissions, RC_HANDLE_CAMERA_PERM);
      }
    };

    Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale, Snackbar.LENGTH_INDEFINITE)
        .setAction(R.string.ok, listener)
        .show();
  }

  @Override public boolean onTouchEvent(MotionEvent e) {
    boolean b = scaleGestureDetector.onTouchEvent(e);

    boolean c = gestureDetector.onTouchEvent(e);

    return b || c || super.onTouchEvent(e);
  }

  /**
   * Creates and starts the camera.  Note that this uses a higher resolution in comparison
   * to other detection examples to enable the ocr detector to detect small text samples
   * at long distances.
   * <p/>
   * Suppressing InlinedApi since there is a check that the minimum version is met before using
   * the constant.
   */
  @SuppressLint("InlinedApi") private void createCameraSource(boolean autoFocus, boolean useFlash) {
    Context context = getApplicationContext();

    // A text recognizer is created to find text.  An associated processor instance
    // is set to receive the text recognition results and display graphics for each text block
    // on screen.
    TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();
    textRecognizer.setProcessor(new OcrDetectorProcessor(mGraphicOverlay));

    if (!textRecognizer.isOperational()) {
      // Note: The first time that an app using a Vision API is installed on a
      // device, GMS will download a native libraries to the device in order to do detection.
      // Usually this completes before the app is run for the first time.  But if that
      // download has not yet completed, then the above call will not detect any text,
      // barcodes, or faces.
      //
      // isOperational() can be used to check if the required native libraries are currently
      // available.  The detectors will automatically become operational once the library
      // downloads complete on device.
      Log.w(TAG, "Detector dependencies are not yet available.");

      // Check for low storage.  If there is low storage, the native library will not be
      // downloaded, so detection will not become operational.
      IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
      boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

      if (hasLowStorage) {
        Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
        Log.w(TAG, getString(R.string.low_storage_error));
      }
    }

    // Creates and starts the camera.  Note that this uses a higher resolution in comparison
    // to other detection examples to enable the text recognizer to detect small pieces of text.

    //todo cambiar el tamaño de la preview para pillar mejor
    mCameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer).setFacing(
        CameraSource.CAMERA_FACING_BACK)
        .setRequestedPreviewSize(1280, 1024)
        .setRequestedFps(2.0f)
        .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
        .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
        .build();
  }

  /**
   * Restarts the camera.
   */
  @Override protected void onResume() {
    super.onResume();
    startCameraSource();
  }

  /**
   * Stops the camera.
   */
  @Override protected void onPause() {
    super.onPause();
    if (mPreview != null) {
      mPreview.stop();
    }
  }

  /**
   * Releases the resources associated with the camera source, the associated detectors, and the
   * rest of the processing pipeline.
   */
  @Override protected void onDestroy() {
    super.onDestroy();
    if (mPreview != null) {
      mPreview.release();
    }
  }

  /**
   * Callback for the result from requesting permissions. This method
   * is invoked for every call on {@link #requestPermissions(String[], int)}.
   * <p>
   * <strong>Note:</strong> It is possible that the permissions request interaction
   * with the user is interrupted. In this case you will receive empty permissions
   * and results arrays which should be treated as a cancellation.
   * </p>
   *
   * @param requestCode The request code passed in {@link #requestPermissions(String[], int)}.
   * @param permissions The requested permissions. Never null.
   * @param grantResults The grant results for the corresponding permissions
   * which is either {@link PackageManager#PERMISSION_GRANTED}
   * or {@link PackageManager#PERMISSION_DENIED}. Never null.
   * @see #requestPermissions(String[], int)
   */
  @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    if (requestCode != RC_HANDLE_CAMERA_PERM) {
      Log.d(TAG, "Got unexpected permission result: " + requestCode);
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      return;
    }

    if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Camera permission granted - initialize the camera source");
      // We have permission, so create the camerasource
      boolean autoFocus = getIntent().getBooleanExtra(App.INTENT_AUTO_FOCUS, false);
      boolean useFlash = getIntent().getBooleanExtra(App.INTENT_USE_FLASH, false);
      createCameraSource(autoFocus, useFlash);
      return;
    }

    Log.e(TAG,
        "Permission not granted: results len = " + grantResults.length + " Result code = " + (
            grantResults.length > 0 ? grantResults[0] : "(empty)"));

    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {
        finish();
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Multitracker sample")
        .setMessage(R.string.no_camera_permission)
        .setPositiveButton(R.string.ok, listener)
        .show();
  }

  /**
   * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
   * (e.g., because onResume was called before the camera source was created), this will be called
   * again when the camera source is created.
   */
  private void startCameraSource() throws SecurityException {
    // Check that the device has play services available.
    int code =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
    if (code != ConnectionResult.SUCCESS) {
      Dialog dlg = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
      dlg.show();
    }

    if (mCameraSource != null) {
      try {
        mPreview.start(mCameraSource, mGraphicOverlay);
      } catch (IOException e) {
        Log.e(TAG, "Unable to start camera source.", e);
        mCameraSource.release();
        mCameraSource = null;
      }
    }
  }

  /**
   * onTap is called to capture the first TextBlock under the tap location and return it to
   * the Initializing Activity.
   *
   * @param rawX - the raw position of the tap
   * @param rawY - the raw position of the tap.
   * @return true if the activity is ending.
   */

  //endregion
  //todo set size and do a pool not infinite arraylist
  //region HANDLER this is the object with CardinfoGetterinfo
  static ArrayList<IDataProvider> mLstDataProviders = new ArrayList<>();
  static ArrayList<Handler> mLstHandlers = new ArrayList<>();
  static ArrayList<Integer> mIdxCardInfoInLstCInfo = new ArrayList<>();
  static ArrayList<Integer> mIdxInPersistor = new ArrayList<>();
  static ArrayList<CardInfo> mLstCardInfo = new ArrayList<>();
  Handler myHandler = new Handler(new Handler.Callback() {
    @Override public boolean handleMessage(Message msg) {
      int idxOfGetterCardInfo = msg.getData().getInt(App.INTENT_REQUEST_KEY);
      int idxOnPersitionDataOfCardinfo = mIdxInPersistor.get(idxOfGetterCardInfo);

      CardInfo cInfFromDataProvider =
          (CardInfo) msg.getData().getSerializable(App.INTENT_CARD_INFO);
      int idxOfLang = msg.getData().getInt(App.INTENT_IDX_DESC);
      //todo hacer el borrado y su persistencia

      switch (msg.what) {
        case DataProviderBase.ERROR:
          //todo buscarlo con otro dataprovider
          // Toast.makeText(this, "Error with" + cInfFromDataProvider.getName(), Toast.LENGTH_LONG).show();
          break;
        case DataProviderBase.PRICE_OK:
          mLstCardInfo.get(idxOfGetterCardInfo).setPrice(cInfFromDataProvider.getPrice());
          mLstCardInfo.get(idxOfGetterCardInfo).setPriceL(cInfFromDataProvider.getPriceL());
          mLstCardInfo.get(idxOfGetterCardInfo).setPriceM(cInfFromDataProvider.getPriceM());
          mLstCardInfo.get(idxOfGetterCardInfo).setPriceH(cInfFromDataProvider.getPriceH());
          break;
        case DataProviderBase.IMG_OK:
          mLstCardInfo.get(idxOfGetterCardInfo).setImgPath(cInfFromDataProvider.getImgPath());
          break;
        case DataProviderBase.DESCRIPTION_OK:
          mLstCardInfo.get(idxOfGetterCardInfo)
              .setDescription(cInfFromDataProvider.getDescription());
          break;
        case DataProviderBase.IMG_DESCRIPTION_OK:
          mLstCardInfo.get(idxOfGetterCardInfo).lstDescription.get(idxOfLang).imgPath =
              cInfFromDataProvider.lstDescription.get(idxOfLang).imgPath;
          break;
        case DataProviderBase.NAME_LANG_DESCRIPTION_OK:
          mLstCardInfo.get(idxOfGetterCardInfo).lstDescription.get(idxOfLang).languague =
              cInfFromDataProvider.lstDescription.get(idxOfLang).languague;
          break;
        case DataProviderBase.NAME_CARD_DESCRIPTION_OK:
          mLstCardInfo.get(idxOfGetterCardInfo).lstDescription.get(idxOfLang).name =
              cInfFromDataProvider.lstDescription.get(idxOfLang).name;
          break;
        case DataProviderBase.DESC_DESCRIPTION_OK:
          mLstCardInfo.get(idxOfGetterCardInfo).lstDescription.get(idxOfLang).description =
              cInfFromDataProvider.lstDescription.get(idxOfLang).description;
          break;
        case DataProviderBase.ALL_DATA_COMPLETE:
          //todo pool
          break;
      }
      CardInfo cardinfoForUpdate = mLstCardInfo.get(idxOfGetterCardInfo);
      updateCardInfoInPersistor(idxOnPersitionDataOfCardinfo, cardinfoForUpdate);
      return false;
    }
  });

  //endregion

  private boolean onTap(float rawX, float rawY) {
    OcrGraphic graphic = mGraphicOverlay.getGraphicAtLocation(rawX, rawY);
    TextBlock text = null;
    if (graphic != null) {
      text = graphic.getTextBlock();
      if (text != null && text.getValue() != null) {

        txtSearch.setText(text.getValue().replace("(", "").replace("|", ""));

        Log.i("", text.getValue());
      } else {
        Log.d(TAG, "text DataUtils is null");
      }
    } else {
      Log.d(TAG, "no text detected");
    }
    return text != null;
  }

  private int getIdxCardInfoInPersistor() {

    if (mPersistorMode.equals("0"))//biblio
    {
      return mBiblio.cards.size() - 1;
    }

    if (mPersistorMode.equals("1"))//newdeck
    {
      return mDecks.decks.size() - 1;
    }

    if (mPersistorMode.equals("2"))//editDecks
    {
      return mDecks.decks.size() - 1;
    }

    return 0;
  }
  //***************
  //
  //
  //
  //
  // *******************************************************************//

  ImageView imgBgCard;

  //region tontimenu

  private void refreshUI() {
    if (mPersistorMode.equals("0"))//biblio
    {
      List<CardInfo> lstGrp = from(mBiblio.cards).orderBy("getName").all();
      showTotalPrice(lstGrp);
      mAdapter = new MyAdapter(lstGrp, this);
      mRecyclerView.setAdapter(mAdapter);
      //todo encontrar una forma mas c00l de hacer esto
    }
    if (mPersistorMode.equals("1"))//newdeck
    {
      mAdapter = new MyAdapter(mBiblio.cards, this);
      mRecyclerView.setAdapter(mAdapter);
    }
    if (mPersistorMode.equals("2"))//Editdeck
    {
      mAdapter = new MyAdapter(mBiblio.cards, this);
      mRecyclerView.setAdapter(mAdapter);
    }
  }
  //endregion

  private void persistInfo(CardInfo cardInfo) {

    if (mPersistorMode.equals("0"))//biblio
    {
      if (cardInfo != null) {
        mBiblio.addCard(cardInfo);
        refreshUI();
      }
      DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
    }
    if (mPersistorMode.equals("1"))//newdeck
    {
      mDecks.decks.get(mDecks.decks.size() - 1).addCard(cardInfo);
      DataUtils.saveSerializable(this, mDecks, mDecks.nameFile);
    }
    if (mPersistorMode.equals("2"))//editDecks
    {
      //todo en vez dle ultimo->mDecks.decks.size()-1
      //editar el current deck
      mDecks.decks.get(mDecks.decks.size() - 1).addCard(cardInfo);
      DataUtils.saveSerializable(this, mDecks, mDecks.nameFile);
    }
  }

  private void updateCardInfoInPersistor(Integer idx, CardInfo cardinfoForUpdate) {

    if (mPersistorMode.equals("0"))//biblio
    {
      if (cardinfoForUpdate != null) {
        mBiblio.cards.set(idx, cardinfoForUpdate);
        refreshUI();
      }
      DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
    }
    if (mPersistorMode.equals("1"))//newdeck
    {
      mDecks.decks.get(mDecks.decks.size() - 1).cards.set(idx, cardinfoForUpdate);
      DataUtils.saveSerializable(this, mDecks, mDecks.nameFile);
    }
    if (mPersistorMode.equals("2"))//editDecks
    {
      //todo en vez dle ultimo->mDecks.decks.size()-1
      //editar el current deck
      mDecks.decks.get(mDecks.decks.size() - 1).cards.set(idx, cardinfoForUpdate);
      DataUtils.saveSerializable(this, mDecks, mDecks.nameFile);
    }
  }

  @Override public void onClick(View v) {
    switch (v.getId()) {

      case R.id.btnOk:
        //todo ver onTap
        doSearch();
        showRecycler();
        break;
      case R.id.btnCancel:
        showRecycler();
        break;
      case R.id.fabOcr:
        showOcr();
        break;
    }
  }

  private void doSearch() {
    //save and show
    CardInfo cardinfo = new CardInfo(txtSearch.getText().toString(), "", "", "", "");
    persistInfo(cardinfo);

    //region create objectGetterCardinfo
    mLstHandlers.add(myHandler);
    int myIdx = mLstHandlers.size() - 1;
    mIdxCardInfoInLstCInfo.add(myIdx);
    mLstCardInfo.add(cardinfo);
    //MtgDataprovider
    IDataProvider myDataProvider = new MtgDataProvider(this, mLstHandlers.get(myIdx), myIdx);
    mLstDataProviders.add(myDataProvider);
    mIdxInPersistor.add(getIdxCardInfoInPersistor());

    // mLstHandlers.set(myIdx, myHandler);
    mLstDataProviders.get(myIdx)
        .GetCardInfo(txtSearch.getText().toString(), mLstCardInfo.get(myIdx));
  }

  private void showOcr() {
    txtSearch.setText("");
    lytRecycler.setVisibility(View.GONE);
    fabOcr.setVisibility(View.GONE);
    topLayout.setVisibility(View.VISIBLE);
    lytSearch.setVisibility(View.VISIBLE);
  }

  private void showRecycler() {

    lytRecycler.setVisibility(View.VISIBLE);
    fabOcr.setVisibility(View.VISIBLE);
    topLayout.setVisibility(View.GONE);
    lytSearch.setVisibility(View.GONE);
  }

  /*******************************************************************************************************/
  private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

    @Override public boolean onSingleTapConfirmed(MotionEvent e) {
      return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
    }
  }

  private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

    /**
     * Responds to scaling events for a gesture in progress.
     * Reported by pointer motion.
     *
     * @param detector The detector reporting the event - use this to
     * retrieve extended info about event state.
     * @return Whether or not the detector should consider this event
     * as handled. If an event was not handled, the detector
     * will continue to accumulate movement until an event is
     * handled. This can be useful if an application, for example,
     * only wants to update scaling factors if the change is
     * greater than 0.01.
     */
    @Override public boolean onScale(ScaleGestureDetector detector) {
      return false;
    }

    /**
     * Responds to the beginning of a scaling gesture. Reported by
     * new pointers going down.
     *
     * @param detector The detector reporting the event - use this to
     * retrieve extended info about event state.
     * @return Whether or not the detector should continue recognizing
     * this gesture. For example, if a gesture is beginning
     * with a focal point outside of a region where it makes
     * sense, onScaleBegin() may return false to ignore the
     * rest of the gesture.
     */
    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
      return true;
    }

    /**
     * Responds to the end of a scale gesture. Reported by existing
     * pointers going up.
     * <p/>
     * Once a scale has ended, {@link ScaleGestureDetector#getFocusX()}
     * and {@link ScaleGestureDetector#getFocusY()} will return focal point
     * of the pointers remaining on the screen.
     *
     * @param detector The detector reporting the event - use this to
     * retrieve extended info about event state.
     */
    @Override public void onScaleEnd(ScaleGestureDetector detector) {
      mCameraSource.doZoom(detector.getScaleFactor());
    }
  }
}
