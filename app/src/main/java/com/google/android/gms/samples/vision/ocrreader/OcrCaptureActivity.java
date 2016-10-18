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
package com.google.android.gms.samples.vision.ocrreader;

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
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.samples.vision.barcodereader.MyAdapter;
import com.google.android.gms.samples.vision.ocrreader.model.Biblio;
import com.google.android.gms.samples.vision.ocrreader.model.CardInfo;
import com.google.android.gms.samples.vision.ocrreader.model.Deck;
import com.google.android.gms.samples.vision.ocrreader.model.Decks;
import com.google.android.gms.samples.vision.ocrreader.model.DescriptionMtgInfo;
import com.google.android.gms.samples.vision.ocrreader.ui.camera.CameraSource;
import com.google.android.gms.samples.vision.ocrreader.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.ocrreader.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.IOException;

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

    // Constants used to pass extra DataUtils in the intent
    public static final String AutoFocus = "AutoFocus";
    public static final String UseFlash = "UseFlash";
    public static final String PersistorMode = "mtgModePersistor";
    public static final String TextBlockObject = "String";

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private GraphicOverlay<OcrGraphic> mGraphicOverlay;

    // Helper objects for detecting taps and pinches.
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;

    String mPersistorMode;
    Biblio mBiblio;
    Decks mDecks;
    Deck mDeck;
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.ocr_capture);
        //region asv
        txtResults = (TextView) findViewById(R.id.txtResults);
        txtDeckResult = (EditText) findViewById(R.id.txtDeckResults);
        txtDescription = (TextView) findViewById(R.id.txtDescription);
        button = (Button) findViewById(R.id.button);
        button2 = (Button) findViewById(R.id.button2);
        button3 = (Button) findViewById(R.id.button3);
        button4 = (Button) findViewById(R.id.button4);
        imgBgCard = (ImageView) findViewById(R.id.imgBgCard);
        imgCard = (ImageView) findViewById(R.id.imgCard);
        tab1 = (RelativeLayout) findViewById(R.id.tab1);
        tab2 = (RelativeLayout) findViewById(R.id.tab2);
        tab3 = (RelativeLayout) findViewById(R.id.tab3);
        tab4 = (RelativeLayout) findViewById(R.id.tab4);

        button.setOnClickListener(this);
        button2.setOnClickListener(this);
        button3.setOnClickListener(this);
        button4.setOnClickListener(this);
        resetmenu();
        //mnu1
        tab1.setVisibility(View.VISIBLE);
        tab1.setBackgroundColor(getResources().getColor(R.color.lDark));
        button.setBackgroundColor(getResources().getColor(R.color.lDark));
        //endregion


        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay<OcrGraphic>) findViewById(R.id.graphicOverlay);

        // read parameters from the intent used to launch the activity.
        boolean autoFocus = getIntent().getBooleanExtra(AutoFocus, false);
        boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);
        mPersistorMode = getIntent().getStringExtra(PersistorMode);


        loadPersistModeDataCardInfo();

        //region RecyclerView
        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        // specify an adapter (see also next example)
        mAdapter = new MyAdapter(mBiblio.cards, this);
        mRecyclerView.setAdapter(mAdapter);
        //endregion
        showPersistorUI();

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(autoFocus, useFlash);
        } else {
            requestCameraPermission();
        }

        gestureDetector = new GestureDetector(this, new CaptureGestureListener());
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

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
            for (CardInfo card : mBiblio.cards) {
                txtResults.setText(txtResults.getText() + card.getName() + " " + card.getPrice() + " €\n");
            }
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

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
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
    @SuppressLint("InlinedApi")
    private void createCameraSource(boolean autoFocus, boolean useFlash) {
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
        mCameraSource =
                new CameraSource.Builder(getApplicationContext(), textRecognizer)
                        .setFacing(CameraSource.CAMERA_FACING_BACK)
                        .setRequestedPreviewSize(1280, 1024)
                        .setRequestedFps(2.0f)
                        .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                        .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
                        .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
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
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // We have permission, so create the camerasource
            boolean autoFocus = getIntent().getBooleanExtra(AutoFocus, false);
            boolean useFlash = getIntent().getBooleanExtra(UseFlash, false);
            createCameraSource(autoFocus, useFlash);
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

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
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
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

    private boolean onTap(float rawX, float rawY) {

        OcrGraphic graphic = mGraphicOverlay.getGraphicAtLocation(rawX, rawY);
        TextBlock text = null;
        if (graphic != null) {
            text = graphic.getTextBlock();
            if (text != null && text.getValue() != null) {
                //todo hacer la peticion una vez se ha validado
                String textForurl = text.getValue().replace(" ", "+").replace("(", "").replace("|", "");
                String UrlBase = "https://es.magiccardmarket.eu/Cards/";
                CardInfo cardinfo = new CardInfo(text.getValue().replace("(", "").replace("|", ""), "", "", "", "");
                //new
                UrlBase = "http://magiccards.info/query?q=";
                getHtmlInfoFromMtginfo(UrlBase + textForurl, text.getValue().replace("(", ""), cardinfo);
                Log.i("", text.getValue());

            } else {
                Log.d(TAG, "text DataUtils is null");
            }
        } else {
            Log.d(TAG, "no text detected");
        }
        return text != null;
    }

    //***************
    //
    //
    //
    //
    // *******************************************************************//


    TextView txtResults, txtDescription;
    EditText txtDeckResult;
    Button button, button2, button3, button4;
    ImageView imgCard, imgBgCard;
    RelativeLayout tab1, tab2, tab3, tab4;

    //region tontimenu
    private void resetmenu() {
        tab1.setVisibility(View.GONE);
        tab2.setVisibility(View.GONE);
        tab3.setVisibility(View.GONE);
        tab4.setVisibility(View.GONE);
        tab1.setBackgroundColor(getResources().getColor(R.color.gDark));
        tab2.setBackgroundColor(getResources().getColor(R.color.gDark));
        tab3.setBackgroundColor(getResources().getColor(R.color.gDark));
        tab4.setBackgroundColor(getResources().getColor(R.color.gDark));

        button.setBackgroundColor(getResources().getColor(R.color.gDark));
        button2.setBackgroundColor(getResources().getColor(R.color.gDark));
        button3.setBackgroundColor(getResources().getColor(R.color.gDark));
        button4.setBackgroundColor(getResources().getColor(R.color.gDark));
    }

    //endregion
    //region scrapeo from mkm
    private void getHtmlInfoFromMkm(String url, String texti, final CardInfo cardInfo) {
        try {
            txtDeckResult.setText(txtDeckResult.getText() + "\n" + texti);
            txtResults.setText(txtResults.getText() + "\n" + texti);

            Ion.with(getApplicationContext()).load(url).asString().setCallback(new FutureCallback<String>() {
                @Override
                public void onCompleted(Exception e, String a) {
                    try {
                        getPriceFromMkm(a, cardInfo);
                        getImgCardFromMkm(a, cardInfo);
                        getTranslateDescriptionFromMkm(a, cardInfo);
                        //->callbackdescriptin persistInfo(cardInfo);
                    } catch (Exception ex) {
                        Log.e("Error,parsing", ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {

            Log.e("Error,HtmlInfo", e.getMessage());
        }
    }

    //region scrapeo macareno
    private void getTranslateDescriptionFromMkm(String a, final CardInfo cardinfo) throws Exception {
        //link al otro idioma
        int ini = a.indexOf("onmouseout=\"hideMsgBox()\"></span><a href=\"");
        int fin = a.indexOf("\" class=\"nameLink\">", ini);
        String urlLang = "https://es.magiccardmarket.eu" + a.substring(ini + "onmouseout=\"hideMsgBox()\"></span><a href=\"".length(), fin);

        Ion.with(getApplicationContext()).load(urlLang).asString().setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String a) {
                //todo parsear la description

                int ini = a.indexOf("<div class=\"infoBlock rulesBlock");
                ini = a.indexOf("itemprop=\"description\">", ini);
                int fin = a.indexOf("</div>", ini);
                String urlLang = a.substring(ini + "itemprop=\"description\">".length(), fin);
                txtDescription.setText(urlLang.replace("<br>", "").replace("<br/>", ""));
                cardinfo.setDescription(txtDescription.getText().toString());
                persistInfo(cardinfo);
            }
        });
    }

    private void getImgCardFromMkm(String a, final CardInfo cardinfo) throws Exception {

        int ini = a.indexOf("id=\"imgDiv\">");
        ini = a.indexOf("<img src=\"", ini);
        int fin = a.indexOf("\" alt=", ini);
        String imgPath = "https://es.magiccardmarket.eu/" + a.substring(ini + "<img src=\"".length(), fin);

        Glide
                .with(imgCard.getContext())
                .load(imgPath)
                .into(imgCard);
        Glide
                .with(imgBgCard.getContext())
                .load(imgPath)
                .into(imgBgCard);

        cardinfo.setImgPath(imgPath);
    }

    private void getPriceFromMkm(String a, final CardInfo cardinfo) throws Exception {
        int ini = a.indexOf("\"lowPrice\">");
        int fin = a.indexOf("</span>", ini);
        String price = a.substring(ini + "\"lowPrice\">".length(), fin);

        if (price.length() > 5)
            price = "caca futi";
        else
            cardinfo.setPrice(price);
        //Intent DataUtils = new Intent();
        txtResults.setText(txtResults.getText() + "   " + price + " €");
    }

    //endregion

    private void getHtmlInfoFromMtginfo(String url, String texti, final CardInfo cardInfo) {
        try {
            txtDeckResult.setText(txtDeckResult.getText() + "\n" + texti);
            txtResults.setText(txtResults.getText() + "\n" + texti);

            Ion.with(getApplicationContext()).load(url).asString().setCallback(new FutureCallback<String>() {
                @Override
                public void onCompleted(Exception e, String a) {
                    try {
                        getPriceFromMtgInfo(a, cardInfo);
                        getImgCardFromMtgInfo(a, cardInfo);
                        getTranslateDescriptionsFromMtgInfo(a, cardInfo);
                        int paradaTontaPaVerqVaEnBiblio = 0;
                        //->callbackdescriptin persistInfo(cardInfo);
                    } catch (Exception ex) {
                        Log.e("Error,parsing", ex.getMessage());
                    }
                }
            });
        } catch (Exception e) {

            Log.e("Error,HtmlInfo", e.getMessage());
        }
    }
    //region scrapeo macareno


    private void getTranslateDescriptionsFromMtgInfo(String a, final CardInfo cardinfo) {
        try {
            //region parseo desc1
            //img banderita
            int ini = a.indexOf("Languages:<");
            ini = a.indexOf("<img src=\"", ini);
            int fin = a.indexOf("\"", ini + "<img src=\"".length() + 1);

            DescriptionMtgInfo descriptionMtgInfo = new DescriptionMtgInfo();
            descriptionMtgInfo.imgPath = a.substring(ini + "<img src=\"".length(), fin);
            cardinfo.lstDescription.add(descriptionMtgInfo);
            //language name
            ini = a.indexOf("alt=\"", fin);
            fin = a.indexOf("\"", ini + "alt=\"".length() + 1);
            cardinfo.lastDescriptionMtgInfoItem().languague = a.substring(ini + "alt=\"".length(), fin);
            //Relative path to language Description
            ini = a.indexOf("<a href=\"", fin);
            fin = a.indexOf("\"", ini + "<a href=\"".length() + 1);
            String urlDesc1 = "http://magiccards.info" + a.substring(ini + "<a href=\"".length(), fin);
            // name card transtaled
            ini = a.indexOf(">", fin);
            fin = a.indexOf("<", ini);
            cardinfo.lastDescriptionMtgInfoItem().name = a.substring(ini + 1, fin);

            //todo parseo link ,img de banderita,nombre en los otros idiomas
            //todo no hay siempre 7 idiomas, debe persertirse aki en el fail de


            //parse 1º idioma y despues trtams de pillar la descripcion de cada idioma,
            // si no existe truena y listo, debemos meter un try cathc individual para cada uno
            //para q pruebe con el siguiente idimoa, el primer idioma lo recuperamos el ultimo y asi hacmeos el persint de la info
//todo falta añadir los addlstdescriptions!!
            //todo refactor necesario
            persistInfo(cardinfo);
            getTranslateDescription1FromMtgInfo(urlDesc1, cardinfo, cardinfo.lstDescription.size());
//todo MEGAIMPORTANTE
            //todo FAIL el name de la carta hay q pilarlo en la web translatada, q si no hay q parsealo aki...one by one
            String languageParsed = "en";//todo ayayaya viva mexico cabrones!

            try {
                if (!languageParsed.equals("fr")) {
                    descriptionMtgInfo = new DescriptionMtgInfo();
                    descriptionMtgInfo.imgPath =
                            cardinfo.lastDescriptionMtgInfoItem().imgPath.replace(languageParsed, "fr");
                    cardinfo.lstDescription.add(descriptionMtgInfo);
                    getTranslateDescription1FromMtgInfo(urlDesc1.replace(languageParsed, "fr"), cardinfo, cardinfo.lstDescription.size());
                }
            } catch (Exception e) {

            }
            try {
                if (!languageParsed.equals("it")) {
                    descriptionMtgInfo = new DescriptionMtgInfo();
                    descriptionMtgInfo.imgPath = cardinfo.lastDescriptionMtgInfoItem()
                            .imgPath.replace(languageParsed, "it");
                    cardinfo.lstDescription.add(descriptionMtgInfo);
                    getTranslateDescription1FromMtgInfo(urlDesc1.replace(languageParsed, "it"), cardinfo, cardinfo.lstDescription.size());
                }
            } catch (Exception e) {

            }
            try {
                if (!languageParsed.equals("pt")) {
                    descriptionMtgInfo = new DescriptionMtgInfo();
                    descriptionMtgInfo.imgPath = cardinfo.lastDescriptionMtgInfoItem()
                            .imgPath.replace(languageParsed, "pt");
                    cardinfo.lstDescription.add(descriptionMtgInfo);
                    getTranslateDescription1FromMtgInfo(urlDesc1.replace(languageParsed, "pt"), cardinfo, cardinfo.lstDescription.size());
                }
            } catch (Exception e) {

            }
            try {
                if (!languageParsed.equals("de")) {
                    descriptionMtgInfo = new DescriptionMtgInfo();
                    descriptionMtgInfo.imgPath = cardinfo.lastDescriptionMtgInfoItem()
                            .imgPath.replace(languageParsed, "de");
                    cardinfo.lstDescription.add(descriptionMtgInfo);
                    getTranslateDescription1FromMtgInfo(urlDesc1.replace(languageParsed, "de"), cardinfo, cardinfo.lstDescription.size());
                }
            } catch (Exception e) {

            }
            try {
                if (!languageParsed.equals("jp")) {
                    descriptionMtgInfo = new DescriptionMtgInfo();
                    descriptionMtgInfo.imgPath = cardinfo.lastDescriptionMtgInfoItem()
                            .imgPath.replace(languageParsed, "jp");
                    cardinfo.lstDescription.add(descriptionMtgInfo);
                    getTranslateDescription1FromMtgInfo(urlDesc1.replace(languageParsed, "jp"), cardinfo, cardinfo.lstDescription.size());
                }
            } catch (Exception e) {

            }
            try {
                if (!languageParsed.equals("cn")) {
                    descriptionMtgInfo = new DescriptionMtgInfo();
                    descriptionMtgInfo.imgPath = cardinfo.lastDescriptionMtgInfoItem()
                            .imgPath.replace(languageParsed, "cn");
                    descriptionMtgInfo.name = "";
                    descriptionMtgInfo.languague = "Chino";
                    cardinfo.lstDescription.add(descriptionMtgInfo);
                    getTranslateDescription1FromMtgInfo(urlDesc1.replace(languageParsed, "cn")
                            , cardinfo, cardinfo.lstDescription.size());
                }
            } catch (Exception e) {

            }


//endregion

        } catch (Exception e) {
            //persist data on fail or in the Descripciont 7 get
            try {
                if (!cardinfo.lastDescriptionMtgInfoItem().description.isEmpty())
                    persistInfo(cardinfo);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    private void getTranslateDescription1FromMtgInfo(String url, final CardInfo cardinfo, final int idx) throws Exception {

        Ion.with(getApplicationContext()).load(url).asString().setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String a) {
                try {
                    int ini = 0;
                    if (a.indexOf("class=\"flag\">") > 0)
                        ini = a.indexOf("class=\"flag\">");
                    else
                        ini = a.indexOf("class=\"flag2\">");

                    ini = a.indexOf("<p>", ini);
                    int fin = a.indexOf("</p>", ini);
                    String typeCard = a.substring(ini + "<p>".length(), fin).replace("\n", " ").replaceAll("<[^>]*>", "");

                    ini = a.indexOf("<p class=\"ctext\">", fin);
                    if (a.indexOf("</i></p>", ini) > 0)
                        fin = a.indexOf("</i></p>", ini);
                    else
                        fin = a.indexOf("</p>", ini);

                    mBiblio.cards.get(mBiblio.cards.size() - 1).lstDescription.get(idx - 1).description = typeCard + "\n" +
                            a.substring(ini, fin).replaceAll("<[^>]*>", "");
                    persistInfo(null);

                    txtDescription.setText(txtDescription.getText() + "\n" + cardinfo.lastDescriptionMtgInfoItem().description);

                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        });
    }


    private void getImgCardFromMtgInfo(String a, final CardInfo cardinfo) throws Exception {

        int ini = a.indexOf("src=\"http://partner.tcgplayer.com/x3/mchl.ashx");
        ini = a.indexOf("<img src=\"", ini);
        int fin = a.indexOf("\"", ini + "<img src=\"".length() + 1);

        String imgPath = a.substring(ini + "<img src=\"".length(), fin);

        Glide
                .with(imgCard.getContext())
                .load(imgPath)
                .into(imgCard);
        Glide
                .with(imgBgCard.getContext())
                .load(imgPath)
                .into(imgBgCard);

        cardinfo.setImgPath(imgPath);
    }

    private void getPriceFromMtgInfo(String a, final CardInfo cardinfo) throws Exception {
        int ini = a.indexOf("src=\"http://partner.tcgplayer.com/");
        int fin = a.indexOf("\"><", ini);
        String urlPriceInfo = a.substring(ini + "src=\"".length(), fin).replace("amp;", "");
        Ion.with(getApplicationContext()).load(urlPriceInfo).asString().setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String a) {
                //todo parsear la description
                int f_ini = a.indexOf("id=\"TCGPHiLoTable\"");
                int ini = a.indexOf("$", f_ini);
                int fin = a.indexOf("<", ini);
                String priceL = a.substring(ini, fin);
                cardinfo.setPriceL(priceL);
                ini = a.indexOf("$", fin);
                fin = a.indexOf("<", ini);
                String priceM = a.substring(ini, fin);
                cardinfo.setPriceM(priceM);
                ini = a.indexOf("$", fin);
                fin = a.indexOf("<", ini);
                String priceH = a.substring(ini, fin);
                cardinfo.setPriceH(priceH);

                cardinfo.setPrice("L: " + priceL + " M: " + priceM + " H: " + priceH);

                txtResults.setText(txtResults.getText() + "L: " + priceL + " M: " + priceM + " H: " + priceH);

            }
        });


    }

    //endregion
    //endregion
    private void persistInfo(CardInfo cardInfo) {

        if (mPersistorMode.equals("0"))//biblio
        {
            if (cardInfo != null) {
                mBiblio.addCard(cardInfo);
                //todo no mola esto aki nada BINDING
                mAdapter = new MyAdapter(mBiblio.cards, this);
                mRecyclerView.setAdapter(mAdapter);
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


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                resetmenu();
                tab1.setVisibility(View.VISIBLE);
                tab1.setBackgroundColor(getResources().getColor(R.color.lDark));
                button.setBackgroundColor(getResources().getColor(R.color.lDark));
                break;
            case R.id.button2:
                resetmenu();
                tab2.setVisibility(View.VISIBLE);
                tab2.setBackgroundColor(getResources().getColor(R.color.lDark));
                button2.setBackgroundColor(getResources().getColor(R.color.lDark));
                break;
            case R.id.button3:
                resetmenu();
                tab3.setVisibility(View.VISIBLE);
                tab3.setBackgroundColor(getResources().getColor(R.color.lDark));
                button3.setBackgroundColor(getResources().getColor(R.color.lDark));


                break;
            case R.id.button4:
                resetmenu();
                tab4.setVisibility(View.VISIBLE);
                tab4.setBackgroundColor(getResources().getColor(R.color.lDark));
                button4.setBackgroundColor(getResources().getColor(R.color.lDark));
                break;
        }

    }

    /*******************************************************************************************************/
    private class CaptureGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return onTap(e.getRawX(), e.getRawY()) || super.onSingleTapConfirmed(e);
        }
    }

    private class ScaleListener implements ScaleGestureDetector.OnScaleGestureListener {

        /**
         * Responds to scaling events for a gesture in progress.
         * Reported by pointer motion.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should consider this event
         * as handled. If an event was not handled, the detector
         * will continue to accumulate movement until an event is
         * handled. This can be useful if an application, for example,
         * only wants to update scaling factors if the change is
         * greater than 0.01.
         */
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            return false;
        }

        /**
         * Responds to the beginning of a scaling gesture. Reported by
         * new pointers going down.
         *
         * @param detector The detector reporting the event - use this to
         *                 retrieve extended info about event state.
         * @return Whether or not the detector should continue recognizing
         * this gesture. For example, if a gesture is beginning
         * with a focal point outside of a region where it makes
         * sense, onScaleBegin() may return false to ignore the
         * rest of the gesture.
         */
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
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
         *                 retrieve extended info about event state.
         */
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mCameraSource.doZoom(detector.getScaleFactor());
        }
    }
}
