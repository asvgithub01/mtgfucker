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
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.Log;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import io.asv.mtgocr.ocrreader.data.DataProviderBase;
import io.asv.mtgocr.ocrreader.data.CardRepository;
import io.asv.mtgocr.ocrreader.data.ScanPrintingPolicy;
import io.asv.mtgocr.ocrreader.data.CardEditionOption;
import io.asv.mtgocr.ocrreader.data.CardIdentificationCandidate;
import io.asv.mtgocr.ocrreader.data.CardIdentificationResult;
import io.asv.mtgocr.ocrreader.data.LocalCardNameMatch;
import io.asv.mtgocr.ocrreader.data.CardNameSuggestion;
import io.asv.mtgocr.ocrreader.data.DeckCatalogStore;
import io.asv.mtgocr.ocrreader.data.IDataProvider;
import io.asv.mtgocr.ocrreader.data.MtgJsonRoomDataProvider;
import io.asv.mtgocr.ocrreader.data.MagicSetOption;
import io.asv.mtgocr.ocrreader.model.Biblio;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.CardCondition;
import io.asv.mtgocr.ocrreader.model.Deck;
import io.asv.mtgocr.ocrreader.model.Decks;
import io.asv.mtgocr.ocrreader.model.DeckCatalog;
import io.asv.mtgocr.ocrreader.model.DeckDefinition;
import io.asv.mtgocr.ocrreader.ui.camera.CameraSource;
import io.asv.mtgocr.ocrreader.ui.camera.CameraSourcePreview;
import io.asv.mtgocr.ocrreader.ui.camera.GraphicOverlay;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Random;

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
  private static final String SCANNER_PREFERENCES = "scanner_preferences";
  private static final String PREF_CLOSE_AFTER_SCAN = "close_after_successful_scan";
  private static final String PREF_AUTO_IDENTIFY = "auto_identify";
  private static final String PREF_QUICK_SCAN = "quick_scan";
  private static final String PREF_LOCKED_SET = "locked_set";
  private static final String PREF_ASK_EDITION_AFTER_SCAN = "ask_edition_after_scan";
  private static final String STATE_SECTION = "selected_section";

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
  private String pendingDetailScrollItemId;
  private int pendingDetailScrollOffset;
  private boolean cardDetailOpen;

  Button btnOk, btnCancel;
  private CheckBox closeAfterScanCheck;
  private CheckBox autoIdentifyCheck;
  private CheckBox quickScanCheck;
  private CheckBox askEditionAfterScanCheck;
  private EditText lockedSetInput;
  private CardScanGuideView cardScanGuide;
  private Button scanSessionButton;
  FloatingActionButton fabOcr;
  EditText txtSearch;
  RelativeLayout lytSearch;
  LinearLayout lytRecycler, topLayout;
  private Spinner sortSpinner, filterSpinner;
  private TextView filterLabel;
  private ArcaneGlassLayout collectionControls;
  private ArcaneGlassLayout setCatalogControls;
  private TextView totalText;
  private TextView setCatalogStatus;
  private ProgressBar setCatalogProgress;
  private EditText setCatalogSearch;
  private MagicSetCatalogAdapter setCatalogAdapter;
  private final List<MagicSetOption> setCatalogItems = new ArrayList<>();
  private boolean setCatalogLoading;
  private ImageButton viewModeButton;
  private BottomNavigationView bottomNavigation;
  private View settingsPlaceholder;
  private View createGroupButton;
  private EditText collectionSearch;
  private int currentSortMode = 0;
  private String currentFilterKey = "all";
  private String currentTextFilter = "";
  private boolean updatingFilterSpinner = false;
  private int artBackgroundRequest = 0;
  private final Random artBackgroundRandom = new Random();
  private final List<GroupFilterOption> filterOptions = new ArrayList<>();
  private static final int SECTION_LIBRARY = 0;
  private static final int SECTION_SETS = 1;
  private static final int SECTION_GROUPS = 2;
  private static final int SECTION_CATALOG = 3;
  private static final int SECTION_SETTINGS = 4;
  private int currentSection = SECTION_LIBRARY;
  private final Handler autoOcrHandler = new Handler(Looper.getMainLooper());
  private CardRepository cardRepository;
  private Runnable pendingNamePrediction;
  private boolean gridMode = false;
  private static final String PREF_COLLECTION_GRID = "collection_grid_mode";
  private static final String PREF_LAST_SET_FILTER = "last_set_filter";
  private static final long NAME_PREDICTION_DELAY_MS = 150L;
  private ListView cardNameSuggestions;
  private ArrayAdapter<String> nameSuggestionAdapter;
  private final List<CardNameSuggestion> currentNameSuggestions = new ArrayList<>();
  private int namePredictionRequest = 0;
  private boolean suppressPredictionWatcher = false;
  private Snackbar activeScanSnackbar;
  private String activeScanCardId;
  private boolean activeScanMetadataFailed;
  private RoundedCardImageView activeScanThumbnail;
  private TextView activeScanMessage;
  private TextView activeScanPrice;
  private final List<CardInfo> scannedSessionCards = new ArrayList<>();
  private ScanSessionAdapter scanSessionAdapter;
  private ToneGenerator scanToneGenerator;
  private final CardScanStability scanStability = new CardScanStability();
  private boolean scanLookupInFlight;
  private boolean scanInProgress;
  private long lastOcrLookupAt;

  /**
   * Initializes the UI and creates the detector pipeline.
   */
  @Override public void onCreate(Bundle icicle) {
    MagicPalette.applyTheme(this);
    super.onCreate(icicle);
    if (icicle != null) currentSection = icicle.getInt(STATE_SECTION, SECTION_LIBRARY);
    setContentView(R.layout.ocr_capture);
    cardRepository = CardRepository.get(this);
    //region asv

    imgBgCard = (ImageView) findViewById(R.id.imgBgCard);
    btnOk = (Button) findViewById(R.id.btnOk);
    btnCancel = (Button) findViewById(R.id.btnCancel);
    closeAfterScanCheck = (CheckBox) findViewById(R.id.checkCloseAfterScan);
    autoIdentifyCheck = (CheckBox) findViewById(R.id.checkAutoIdentify);
    quickScanCheck = (CheckBox) findViewById(R.id.checkQuickScan);
    askEditionAfterScanCheck = (CheckBox) findViewById(R.id.checkAskEditionAfterScan);
    lockedSetInput = (EditText) findViewById(R.id.txtLockedSet);
    cardScanGuide = (CardScanGuideView) findViewById(R.id.cardScanGuide);
    scanSessionButton = (Button) findViewById(R.id.btnScanSession);
    fabOcr = (FloatingActionButton) findViewById(R.id.fabOcr);
    txtSearch = (EditText) findViewById(R.id.txtSearch);
    cardNameSuggestions = (ListView) findViewById(R.id.cardNameSuggestions);
    lytSearch = (RelativeLayout) findViewById(R.id.lytSearch);

    lytRecycler = (LinearLayout) findViewById(R.id.lytRecycler);
    topLayout = (LinearLayout) findViewById(R.id.topLayout);
    btnOk.setOnClickListener(this);
    btnCancel.setOnClickListener(this);
    closeAfterScanCheck.setChecked(getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE)
        .getBoolean(PREF_CLOSE_AFTER_SCAN, false));
    closeAfterScanCheck.setOnCheckedChangeListener((button, checked) ->
        getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(PREF_CLOSE_AFTER_SCAN, checked)
            .apply());
    autoIdentifyCheck.setChecked(getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE)
        .getBoolean(PREF_AUTO_IDENTIFY, true));
    quickScanCheck.setChecked(getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE)
        .getBoolean(PREF_QUICK_SCAN, true));
    askEditionAfterScanCheck.setChecked(
        getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE)
            .getBoolean(PREF_ASK_EDITION_AFTER_SCAN, false));
    lockedSetInput.setText(getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE)
        .getString(PREF_LOCKED_SET, ""));
    autoIdentifyCheck.setOnCheckedChangeListener((button, checked) ->
        getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(PREF_AUTO_IDENTIFY, checked).apply());
    quickScanCheck.setOnCheckedChangeListener((button, checked) ->
        getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(PREF_QUICK_SCAN, checked).apply());
    askEditionAfterScanCheck.setOnCheckedChangeListener((button, checked) ->
        getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(PREF_ASK_EDITION_AFTER_SCAN, checked).apply());
    lockedSetInput.setOnFocusChangeListener((view, focused) -> {
      if (!focused) getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE).edit()
          .putString(PREF_LOCKED_SET, lockedSetInput.getText().toString().trim()).apply();
    });
    cardScanGuide.setMessage(getString(R.string.scan_align_card));
    scanSessionAdapter = new ScanSessionAdapter(this, scannedSessionCards,
        this::showCardConditionPicker);
    scanSessionButton.setOnClickListener(view -> showScanSession());
    scanToneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
    fabOcr.setOnClickListener(this);
    setUpNamePredictor();
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
    setUpCollectionControls();
    setUpSetCatalog();
    setUpBottomNavigation();
    topLayout.setVisibility(View.GONE);
    showSelectedSection();
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

  @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putInt(STATE_SECTION, currentSection);
    super.onSaveInstanceState(outState);
  }

  //region recycler

  private void setUpNamePredictor() {
    nameSuggestionAdapter = new ArrayAdapter<>(
        this, R.layout.name_suggestion_item, new ArrayList<String>());
    cardNameSuggestions.setAdapter(nameSuggestionAdapter);
    cardNameSuggestions.setOnItemClickListener((parent, view, position, id) -> {
      if (position < 0 || position >= currentNameSuggestions.size()) return;
      CardNameSuggestion suggestion = currentNameSuggestions.get(position);
      suppressPredictionWatcher = true;
      txtSearch.setText(suggestion.getDisplayName());
      txtSearch.setSelection(txtSearch.length());
      suppressPredictionWatcher = false;
      hideNamePredictions();
      InputMethodManager keyboard =
          (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      keyboard.hideSoftInputFromWindow(txtSearch.getWindowToken(), 0);
      txtSearch.clearFocus();
    });
    txtSearch.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

      @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
        if (!suppressPredictionWatcher) {
          scheduleNamePredictions(text == null ? "" : text.toString());
        }
      }

      @Override public void afterTextChanged(Editable editable) { }
    });
    txtSearch.setOnEditorActionListener((view, actionId, event) -> {
      if (actionId != EditorInfo.IME_ACTION_DONE) return false;
      String selectedName = txtSearch.getText().toString().trim();
      if (selectedName.length() == 0) return true;
      submitScannedCard(selectedName);
      return true;
    });
    cardRepository.prepareCardNamePredictor(ready -> kotlin.Unit.INSTANCE);
  }

  private void scheduleNamePredictions(String query) {
    if (pendingNamePrediction != null) autoOcrHandler.removeCallbacks(pendingNamePrediction);
    final String trimmed = query.trim();
    if (trimmed.length() < 2 || lytSearch.getVisibility() != View.VISIBLE) {
      hideNamePredictions();
      return;
    }
    final int request = ++namePredictionRequest;
    pendingNamePrediction = () -> cardRepository.suggestCardNames(trimmed, suggestions -> {
      if (request != namePredictionRequest || lytSearch.getVisibility() != View.VISIBLE ||
          !txtSearch.getText().toString().trim().equals(trimmed)) {
        return kotlin.Unit.INSTANCE;
      }
      currentNameSuggestions.clear();
      currentNameSuggestions.addAll(suggestions);
      nameSuggestionAdapter.clear();
      for (CardNameSuggestion suggestion : suggestions) {
        nameSuggestionAdapter.add(getString(
            R.string.suggestion_with_language,
            suggestion.getDisplayName(),
            suggestion.getLanguage()
        ));
      }
      nameSuggestionAdapter.notifyDataSetChanged();
      cardNameSuggestions.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
      return kotlin.Unit.INSTANCE;
    });
    autoOcrHandler.postDelayed(pendingNamePrediction, NAME_PREDICTION_DELAY_MS);
  }

  private void hideNamePredictions() {
    namePredictionRequest++;
    currentNameSuggestions.clear();
    if (nameSuggestionAdapter != null) nameSuggestionAdapter.clear();
    if (cardNameSuggestions != null) cardNameSuggestions.setVisibility(View.GONE);
  }

  private void handleAutomaticOcr(List<String> candidates) {
    if (autoIdentifyCheck == null || !autoIdentifyCheck.isChecked() ||
        lytSearch.getVisibility() != View.VISIBLE || scanLookupInFlight || scanInProgress) return;
    long now = SystemClock.elapsedRealtime();
    if (now - lastOcrLookupAt < 250L) return;
    lastOcrLookupAt = now;
    scanLookupInFlight = true;
    cardRepository.matchLocalOcrText(candidates, match -> {
      scanLookupInFlight = false;
      if (match == null || lytSearch.getVisibility() != View.VISIBLE || scanInProgress) {
        return kotlin.Unit.INSTANCE;
      }
      String displayName = match.getDisplayName();
      cardScanGuide.setMessage(getString(R.string.scan_reading_name, displayName));
      suppressPredictionWatcher = true;
      txtSearch.setText(displayName);
      suppressPredictionWatcher = false;
      if (scanStability.observe(match.getCanonicalName(), SystemClock.elapsedRealtime())) {
        captureArtworkForIdentification(match);
      }
      return kotlin.Unit.INSTANCE;
    });
  }

  private void captureArtworkForIdentification(LocalCardNameMatch match) {
    if (mCameraSource == null || scanInProgress) return;
    scanInProgress = true;
    if (quickScanCheck.isChecked()) {
      cardScanGuide.setMessage(getString(R.string.scan_reading_name, match.getDisplayName()));
      cardRepository.quickScanCard(match.getCanonicalName(), lockedSetCodes(), (option, error) -> {
        if (lytSearch.getVisibility() != View.VISIBLE) {
          scanInProgress = false;
        } else if (error != null) {
          scanInProgress = false;
          cardScanGuide.setMessage(getString(lockedSetCodes().isEmpty()
              ? R.string.scan_identification_failed : R.string.scan_no_set_match));
        } else if (option == null) {
          // A brand-new name is still added instantly; image, printing and price arrive in the
          // existing background metadata pipeline and update both snackbar and session history.
          scanInProgress = false;
          submitScannedCard(match.getDisplayName());
        } else {
          addIdentifiedPrinting(option);
        }
        return kotlin.Unit.INSTANCE;
      });
      return;
    }
    cardScanGuide.setMessage(getString(R.string.scan_comparing_art, match.getDisplayName()));
    try {
      mCameraSource.takePicture(null, jpeg -> cardRepository.identifyCardArtwork(
          match.getCanonicalName(), jpeg, lockedSetCodes(), (result, error) -> {
            handleArtworkIdentification(match, result, error);
            return kotlin.Unit.INSTANCE;
          }));
    } catch (RuntimeException error) {
      Log.w(TAG, "No se pudo capturar la carta", error);
      scanInProgress = false;
      cardScanGuide.setMessage(getString(R.string.scan_identification_failed));
    }
  }

  private Set<String> lockedSetCodes() {
    Set<String> result = new LinkedHashSet<>();
    String value = lockedSetInput == null ? "" : lockedSetInput.getText().toString();
    for (String token : value.split("[,\\s]+")) {
      if (!token.trim().isEmpty()) result.add(token.trim().toUpperCase(Locale.US));
    }
    return result;
  }

  private void handleArtworkIdentification(LocalCardNameMatch nameMatch,
      CardIdentificationResult result, Throwable error) {
    if (isFinishing() || isDestroyed() || lytSearch.getVisibility() != View.VISIBLE) {
      scanInProgress = false;
      return;
    }
    List<CardIdentificationCandidate> candidates = result.getCandidates();
    if (error != null || candidates.isEmpty()) {
      Log.w(TAG, "No se pudo resolver la impresión por ilustración", error);
      scanInProgress = false;
      cardScanGuide.setMessage(getString(lockedSetCodes().isEmpty()
          ? R.string.scan_identification_failed : R.string.scan_no_set_match));
      suppressPredictionWatcher = true;
      txtSearch.setText(nameMatch.getDisplayName());
      txtSearch.setSelection(txtSearch.length());
      suppressPredictionWatcher = false;
      return;
    }
    if (!askEditionAfterScanCheck.isChecked()) {
      List<CardEditionOption> options = new ArrayList<>();
      for (CardIdentificationCandidate candidate : candidates) options.add(candidate.getOption());
      CardEditionOption preferred = ScanPrintingPolicy.preferred(options);
      if (preferred != null) addIdentifiedPrinting(preferred);
      else {
        scanInProgress = false;
        cardScanGuide.setMessage(getString(R.string.scan_identification_failed));
      }
      return;
    }
    if (candidates.size() == 1) {
      addIdentifiedPrinting(candidates.get(0).getOption());
      return;
    }
    String[] labels = new String[candidates.size()];
    for (int index = 0; index < candidates.size(); index++) {
      CardIdentificationCandidate candidate = candidates.get(index);
      CardEditionOption option = candidate.getOption();
      labels[index] = getString(
          R.string.scan_candidate_label,
          option.getSetName(),
          option.getSetCode(),
          option.getCollectorNumber(),
          Math.max(0, Math.round((1d - candidate.getDistance()) * 100d))
      );
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.scan_choose_printing)
        .setItems(labels, (dialog, which) -> addIdentifiedPrinting(candidates.get(which).getOption()))
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
          scanInProgress = false;
          scanStability.allowRepeat();
          cardScanGuide.setMessage(getString(R.string.scan_align_card));
        })
        .setOnCancelListener(dialog -> {
          scanInProgress = false;
          scanStability.allowRepeat();
          cardScanGuide.setMessage(getString(R.string.scan_align_card));
        })
        .show();
  }

  private void addIdentifiedPrinting(CardEditionOption option) {
    CardInfo card = new CardInfo(option.getDisplayName(), "", "", "", "1");
    applyEditionMetadata(card, option);
    persistInfo(card);
    cardRepository.selectEdition(card.getCollectionItemId(), option, () -> kotlin.Unit.INSTANCE);
    enrichIdentifiedPrinting(card.getCollectionItemId(), option);
    scanInProgress = false;
    if (closeAfterScanCheck.isChecked()) {
      showRecycler();
    } else {
      prepareScannerForNextCard();
      cardScanGuide.setMessage(getString(R.string.scan_tap_repeat));
    }
  }

  private void applyEditionMetadata(CardInfo card, CardEditionOption option) {
    card.setName(option.getDisplayName());
    card.setDescription(TextUtils.join("\n", java.util.Arrays.asList(
        option.getTypeLine(), option.getRulesText())).trim());
    card.setImgPath(option.getImageUrl() == null ? "" : option.getImageUrl());
    card.setPrintingUuid(option.getPrintingUuid());
    card.setSetCode(option.getSetCode());
    card.setSetName(option.getSetName());
    card.setCollectorNumber(option.getCollectorNumber());
    card.setFinish(option.getFinish());
    if (option.getPrice() != null) {
      String currency = option.getCurrency() == null ? "" : option.getCurrency();
      String display = String.format(Locale.US, "%.2f %s", option.getPrice(), currency).trim();
      card.setPrice(display);
      card.setPriceL(option.getPrice().toString());
      card.setPriceM(option.getPrice().toString());
      card.setPriceH(option.getPrice().toString());
    }
  }

  /** Completes the exact auto-selected printing in the background just as the detail screen does. */
  private void enrichIdentifiedPrinting(String collectionItemId, CardEditionOption selectedOption) {
    cardRepository.loadCard(selectedOption.getCardName(), false, true, (options, error) -> {
      if (error != null || options == null || options.isEmpty() || isFinishing() || isDestroyed()) {
        return kotlin.Unit.INSTANCE;
      }
      CardEditionOption refreshed = null;
      for (CardEditionOption candidate : options) {
        if (selectedOption.getPrintingUuid().equals(candidate.getPrintingUuid()) &&
            selectedOption.getFinish().equalsIgnoreCase(candidate.getFinish())) {
          refreshed = candidate;
          break;
        }
      }
      if (refreshed == null) return kotlin.Unit.INSTANCE;
      CardInfo current = findCollectionCard(collectionItemId);
      if (current == null) return kotlin.Unit.INSTANCE;
      applyEditionMetadata(current, refreshed);
      DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
      rememberSessionScan(current);
      updateCardAddedSnackbar(current, false);
      return kotlin.Unit.INSTANCE;
    });
  }

  private void setUpRecyclerView() {
    mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
    mRecyclerView.setHasFixedSize(true);
    gridMode = getPreferences(MODE_PRIVATE).getBoolean(PREF_COLLECTION_GRID, false);
    applyCollectionLayoutMode();

    refreshUI();
    setUpItemTouchHelper();
    setUpAnimationDecoratorHelper();
    //https://github.com/iPaulPro/Android-ItemTouchHelper-Demo/blob/master/app/src/main/java/co/paulburke/android/itemtouchhelperdemo/RecyclerGridFragment.java
  }

  private void setUpCollectionControls() {
    collectionControls = findViewById(R.id.collectionControls);
    totalText = (TextView) findViewById(R.id.txtTotal);
    if (!"0".equals(mPersistorMode)) {
      collectionControls.setVisibility(View.GONE);
      return;
    }

    sortSpinner = (Spinner) findViewById(R.id.spinnerCollectionSort);
    filterSpinner = (Spinner) findViewById(R.id.spinnerCollectionFilter);
    filterLabel = (TextView) findViewById(R.id.txtCollectionFilterLabel);
    collectionSearch = (EditText) findViewById(R.id.txtCollectionSearch);
    viewModeButton = (ImageButton) findViewById(R.id.btnCollectionViewMode);
    updateViewModeButton();
    viewModeButton.setOnClickListener(view -> {
      gridMode = !gridMode;
      getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_COLLECTION_GRID, gridMode).apply();
      applyCollectionLayoutMode();
      updateViewModeButton();
      refreshUI();
    });
    collectionSearch.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
      @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
        currentTextFilter = text == null ? "" : text.toString();
        refreshUI();
      }
      @Override public void afterTextChanged(Editable editable) { }
    });
    mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        // The controls become denser once cards are moving beneath them, then clear again at top.
        collectionControls.setGlassIntensity(recyclerView.canScrollVertically(-1) ? 1.12f : .82f);
      }
    });
    ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(
        this,
        R.array.collection_sort_options,
        R.layout.spinner_item
    );
    sortAdapter.setDropDownViewResource(R.layout.spinner_item);
    sortSpinner.setAdapter(sortAdapter);
    sortSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
      if (currentSortMode != position) {
        currentSortMode = position;
        refreshUI();
      }
      return kotlin.Unit.INSTANCE;
    }));
    updateFilterOptions();
    filterSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
      if (!updatingFilterSpinner && position >= 0 && position < filterOptions.size()) {
        String selectedKey = filterOptions.get(position).key;
        if (!selectedKey.equals(currentFilterKey)) {
          currentFilterKey = selectedKey;
          if (currentSection == SECTION_SETS && selectedKey.startsWith("set:")) {
            getPreferences(MODE_PRIVATE).edit().putString(PREF_LAST_SET_FILTER, selectedKey).apply();
          }
          refreshUI();
          if (currentSection == SECTION_SETS) showRandomExpansionBackground();
        }
      }
      return kotlin.Unit.INSTANCE;
    }));
  }

  private void setUpSetCatalog() {
    setCatalogControls = findViewById(R.id.setCatalogControls);
    setCatalogSearch = findViewById(R.id.txtSetCatalogSearch);
    setCatalogStatus = findViewById(R.id.txtSetCatalogStatus);
    setCatalogProgress = findViewById(R.id.setCatalogProgress);
    setCatalogProgress.setVisibility(View.GONE);
    setCatalogAdapter = new MagicSetCatalogAdapter(this, set -> {
      startActivity(new Intent(this, SetCollectionActivity.class)
          .putExtra(SetCollectionActivity.EXTRA_SET_CODE, set.getCode())
          .putExtra(SetCollectionActivity.EXTRA_SET_NAME, set.getName()));
      return kotlin.Unit.INSTANCE;
    });
    setCatalogSearch.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
      @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
        setCatalogAdapter.filter(text == null ? "" : text.toString());
        updateSetCatalogCount();
      }
      @Override public void afterTextChanged(Editable editable) { }
    });
  }

  private void showSetCatalog() {
    mRecyclerView.setAdapter(setCatalogAdapter);
    if (!setCatalogItems.isEmpty()) {
      setCatalogAdapter.submit(setCatalogItems);
      setCatalogAdapter.filter(setCatalogSearch.getText().toString());
      updateSetCatalogCount();
      return;
    }
    if (setCatalogLoading) return;
    setCatalogLoading = true;
    setCatalogProgress.setVisibility(View.VISIBLE);
    setCatalogStatus.setText(R.string.loading_set_catalog);
    cardRepository.loadSetCatalog(false, (sets, error) -> {
      setCatalogLoading = false;
      setCatalogProgress.setVisibility(View.GONE);
      if (error != null) {
        setCatalogStatus.setText(error.getMessage() == null
            ? getString(R.string.set_catalog_error) : error.getMessage());
        return kotlin.Unit.INSTANCE;
      }
      setCatalogItems.clear();
      setCatalogItems.addAll(sets);
      setCatalogAdapter.submit(setCatalogItems);
      setCatalogAdapter.filter(setCatalogSearch.getText().toString());
      updateSetCatalogCount();
      return kotlin.Unit.INSTANCE;
    });
  }

  private void updateSetCatalogCount() {
    if (setCatalogLoading) return;
    int count = setCatalogAdapter.visibleCount();
    setCatalogStatus.setText(getResources().getQuantityString(
        R.plurals.set_catalog_count, count, count));
  }

  private void setUpBottomNavigation() {
    bottomNavigation = (BottomNavigationView) findViewById(R.id.bottomNavigation);
    settingsPlaceholder = findViewById(R.id.settingsPlaceholder);
    setUpPaletteSettings();
    createGroupButton = findViewById(R.id.btnCreateGroup);
    createGroupButton.setOnClickListener(view -> promptForDeckCreation(null));
    if (!"0".equals(mPersistorMode)) {
      bottomNavigation.setVisibility(View.GONE);
      return;
    }
    bottomNavigation.setOnItemSelectedListener(item -> {
      int itemId = item.getItemId();
      if (itemId == R.id.nav_library) currentSection = SECTION_LIBRARY;
      else if (itemId == R.id.nav_sets) currentSection = SECTION_SETS;
      else if (itemId == R.id.nav_groups) currentSection = SECTION_GROUPS;
      else if (itemId == R.id.nav_catalog) currentSection = SECTION_CATALOG;
      else if (itemId == R.id.nav_settings) currentSection = SECTION_SETTINGS;
      if (currentSection == SECTION_SETS) {
        currentFilterKey = getPreferences(MODE_PRIVATE).getString(PREF_LAST_SET_FILTER, "");
      } else {
        currentFilterKey = "all";
      }
      showSelectedSection();
      return true;
    });
    int selectedNavigation = currentSection == SECTION_SETS ? R.id.nav_sets
        : currentSection == SECTION_GROUPS ? R.id.nav_groups
        : currentSection == SECTION_CATALOG ? R.id.nav_catalog
        : currentSection == SECTION_SETTINGS ? R.id.nav_settings
        : R.id.nav_library;
    bottomNavigation.setSelectedItemId(selectedNavigation);
  }

  private void setUpPaletteSettings() {
    RadioGroup paletteGroup = findViewById(R.id.paletteRadioGroup);
    String selected = MagicPalette.selectedId(this);
    int selectedButton = MagicPalette.RED.equals(selected) ? R.id.paletteRed
        : MagicPalette.BLUE.equals(selected) ? R.id.paletteBlue
        : MagicPalette.BLACK.equals(selected) ? R.id.paletteBlack
        : MagicPalette.WHITE.equals(selected) ? R.id.paletteWhite
        : MagicPalette.METAL.equals(selected) ? R.id.paletteMetal
        : R.id.paletteGreen;
    paletteGroup.check(selectedButton);
    paletteGroup.setOnCheckedChangeListener((group, checkedId) -> {
      String palette = checkedId == R.id.paletteRed ? MagicPalette.RED
          : checkedId == R.id.paletteBlue ? MagicPalette.BLUE
          : checkedId == R.id.paletteBlack ? MagicPalette.BLACK
          : checkedId == R.id.paletteWhite ? MagicPalette.WHITE
          : checkedId == R.id.paletteMetal ? MagicPalette.METAL
          : MagicPalette.GREEN;
      if (MagicPalette.select(this, palette)) recreate();
    });
  }

  private void showSelectedSection() {
    boolean settings = currentSection == SECTION_SETTINGS;
    boolean catalog = currentSection == SECTION_CATALOG;
    lytRecycler.setVisibility(settings ? View.GONE : View.VISIBLE);
    settingsPlaceholder.setVisibility(settings ? View.VISIBLE : View.GONE);
    collectionControls.setVisibility(!settings && !catalog ? View.VISIBLE : View.GONE);
    setCatalogControls.setVisibility(!settings && catalog ? View.VISIBLE : View.GONE);
    totalText.setVisibility(!settings && !catalog ? View.VISIBLE : View.GONE);
    fabOcr.setVisibility(settings || catalog ? View.GONE : View.VISIBLE);
    if (createGroupButton != null) {
      createGroupButton.setVisibility(!settings && currentSection == SECTION_GROUPS ? View.VISIBLE : View.GONE);
    }
    if (!settings) {
      if (catalog) mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
      else applyCollectionLayoutMode();
      refreshUI();
    }
    if (!settings) updateSectionBackground();
  }

  private void updateSectionBackground() {
    boolean library = currentSection == SECTION_LIBRARY;
    boolean expansions = currentSection == SECTION_SETS;
    boolean artworkBackground = library || expansions;
    artBackgroundRequest++;
    imgBgCard.setVisibility(artworkBackground ? View.VISIBLE : View.GONE);
    mRecyclerView.setBackgroundColor(artworkBackground ? Color.TRANSPARENT : MagicPalette.backgroundColor(this));
    lytRecycler.setBackgroundColor(Color.TRANSPARENT);
    if (collectionControls != null) {
      collectionControls.setGlassIntensity(artworkBackground ? .94f :
          (mRecyclerView.canScrollVertically(-1) ? 1.12f : .82f));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      imgBgCard.setRenderEffect(artworkBackground
          ? RenderEffect.createBlurEffect(9f, 9f, Shader.TileMode.CLAMP)
          : null);
    }
    if (library) showRandomLibraryBackground();
    else if (expansions) showRandomExpansionBackground();
  }

  private int getColorCompat(int colorResource) {
    return ContextCompat.getColor(this, colorResource);
  }

  private void showRandomLibraryBackground() {
    if (mBiblio == null || currentSection != SECTION_LIBRARY) return;
    ++artBackgroundRequest;
    List<CardInfo> candidates = new ArrayList<>();
    for (CardInfo card : mBiblio.cards) {
      if (safe(card.getImgPath()).trim().length() > 0) candidates.add(card);
    }
    if (candidates.isEmpty()) {
      imgBgCard.setImageDrawable(null);
      return;
    }
    CardInfo chosen = candidates.get(artBackgroundRandom.nextInt(candidates.size()));
    displayArtworkBackground(chosen.getImgPath());
  }

  private void showRandomExpansionBackground() {
    if (mBiblio == null || currentSection != SECTION_SETS) return;
    final int request = ++artBackgroundRequest;
    List<CardInfo> candidates = new ArrayList<>();
    for (CardInfo card : mBiblio.cards) {
      if (("all".equals(currentFilterKey) || matchesCurrentFilter(card)) &&
          safe(card.getImgPath()).trim().length() > 0) {
        candidates.add(card);
      }
    }
    if (!candidates.isEmpty()) {
      CardInfo chosen = candidates.get(artBackgroundRandom.nextInt(candidates.size()));
      displayArtworkBackground(chosen.getImgPath());
      return;
    }

    CardInfo expansionCard = null;
    for (CardInfo card : mBiblio.cards) {
      if ("all".equals(currentFilterKey) || setFilterKey(card).equals(currentFilterKey)) {
        expansionCard = card;
        break;
      }
    }
    if (expansionCard == null || safe(expansionCard.getSetCode()).trim().length() == 0) {
      imgBgCard.setImageDrawable(null);
      return;
    }
    cardRepository.loadSet(expansionCard.getSetCode(), (cards, error) -> {
      if (request != artBackgroundRequest || currentSection != SECTION_SETS || error != null) {
        return kotlin.Unit.INSTANCE;
      }
      List<String> remoteImages = new ArrayList<>();
      for (io.asv.mtgocr.ocrreader.data.SetCardOption option : cards) {
        if (option.getImageUrl() != null && option.getImageUrl().trim().length() > 0) {
          remoteImages.add(option.getImageUrl());
        }
      }
      if (!remoteImages.isEmpty()) {
        displayArtworkBackground(remoteImages.get(artBackgroundRandom.nextInt(remoteImages.size())));
      }
      return kotlin.Unit.INSTANCE;
    });
  }

  private void displayArtworkBackground(String imageUrl) {
    imgBgCard.animate().cancel();
    CardImageCache.displayKeepingCurrent(this, imageUrl, imgBgCard);
    imgBgCard.animate().alpha(0.78f).setDuration(320L).start();
  }

  private void showTotalPrice(List<CardInfo> lstGrp) {
    try {
      float sum = 0.0f;
      for (int i = 0; i < lstGrp.size(); i++) {
        CardInfo card = lstGrp.get(i);
        sum = sum + ((float) parseCardPrice(card) * card.getQuantityCount());
      }
      TextView txtTotal = (TextView) findViewById(R.id.txtTotal);
      txtTotal.setText(String.format(Locale.getDefault(), "Total Price: %.2f", sum));
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
            if (!(recyclerView.getAdapter() instanceof MyAdapter) || currentSection == SECTION_GROUPS) return 0;
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
              CardInfo removedCard = adapter.getItem(swipedPosition);
              adapter.remove(swipedPosition);
              deleteCardFromCollection(removedCard);
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
      } else {
        boolean migratedCollectionIds = false;
        long fallbackAddedAt = System.currentTimeMillis() - mBiblio.cards.size();
        for (int index = 0; index < mBiblio.cards.size(); index++) {
          CardInfo card = mBiblio.cards.get(index);
          migratedCollectionIds |= card.ensureCollectionItemId();
          migratedCollectionIds |= card.ensureAddedAt(fallbackAddedAt + index);
          migratedCollectionIds |= card.ensureQuantity();
          card.getGroups();
          card.getDecks();
        }
        migratedCollectionIds |= consolidateIdenticalCopies();
        if (migratedCollectionIds) {
          DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
        }
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
    textRecognizer.setProcessor(new OcrDetectorProcessor(mGraphicOverlay,
        candidates -> runOnUiThread(() -> handleAutomaticOcr(candidates))));

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
        .setRequestedFps(4.0f)
        .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
        .setFocusMode(autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null)
        .build();
  }

  /**
   * Restarts the camera.
   */
  @Override protected void onResume() {
    super.onResume();
    boolean returningFromCardDetail = cardDetailOpen;
    if ("0".equals(mPersistorMode) && mBiblio != null) {
      Biblio persisted = DataUtils.readSerializable(this, mBiblio.nameFile);
      if (persisted != null) {
        mBiblio = persisted;
        if (consolidateIdenticalCopies()) {
          DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
        }
        if (mRecyclerView != null) refreshUI();
      }
    }
    if (returningFromCardDetail) {
      cardDetailOpen = false;
      pendingDetailScrollItemId = null;
    }
    updateScanSessionUi();
    startCameraSource();
  }

  private void applyCollectionLayoutMode() {
    mLayoutManager = gridMode && "0".equals(mPersistorMode)
        ? new GridLayoutManager(this, 2)
        : new LinearLayoutManager(this);
    mRecyclerView.setLayoutManager(mLayoutManager);
  }

  private void updateViewModeButton() {
    if (viewModeButton == null) return;
    viewModeButton.setImageResource(gridMode
        ? android.R.drawable.ic_menu_sort_by_size
        : android.R.drawable.ic_menu_gallery);
    viewModeButton.setContentDescription(getString(gridMode
        ? R.string.show_as_list
        : R.string.show_as_grid));
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
    autoOcrHandler.removeCallbacksAndMessages(null);
    if (activeScanSnackbar != null) activeScanSnackbar.dismiss();
    activeScanSnackbar = null;
    activeScanThumbnail = null;
    activeScanMessage = null;
    activeScanPrice = null;
    if (scanToneGenerator != null) {
      scanToneGenerator.release();
      scanToneGenerator = null;
    }
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
          mLstCardInfo.get(idxOfGetterCardInfo).setPrice(cInfFromDataProvider.getBasePrice());
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
          mLstCardInfo.set(idxOfGetterCardInfo, cInfFromDataProvider);
          break;
      }
      CardInfo cardinfoForUpdate = mLstCardInfo.get(idxOfGetterCardInfo);
      updateCardInfoInPersistor(idxOnPersitionDataOfCardinfo, cardinfoForUpdate);
      updateCardAddedSnackbar(cardinfoForUpdate, msg.what == DataProviderBase.ERROR);
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
        String firstPhrase = OcrTextSelection.firstPhrase(text.getValue());
        if (!firstPhrase.isEmpty()) {
          // Mobile Vision groups several card lines into one TextBlock. The card name is the
          // first line; never copy the rules text, type line, artist, etc. into the search field.
          txtSearch.setText(firstPhrase);
          txtSearch.setSelection(firstPhrase.length());
          Log.i(TAG, "OCR phrase selected manually: " + firstPhrase);
        }
      } else {
        Log.d(TAG, "text DataUtils is null");
      }
    } else {
      Log.d(TAG, "no text detected");
      if (lytSearch.getVisibility() == View.VISIBLE && autoIdentifyCheck.isChecked() && !scanInProgress) {
        scanStability.allowRepeat();
        cardScanGuide.setMessage(getString(R.string.scan_align_card));
        if (mCameraSource != null) mCameraSource.autoFocus(null);
        return true;
      }
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
      if (currentSection == SECTION_CATALOG) {
        showSetCatalog();
        return;
      }
      updateFilterOptions();
      if (currentSection == SECTION_GROUPS && "all".equals(currentFilterKey)) {
        showDeckSummaries();
        return;
      }
      List<CardInfo> visibleCards = new ArrayList<>();
      for (CardInfo card : mBiblio.cards) {
        CardImageCache.prefetch(this, card.getImgPath());
        if (matchesCurrentFilter(card)) visibleCards.add(card);
      }
      sortCards(visibleCards);
      visibleCards = clusterSameNamedCards(visibleCards);
      showTotalPrice(visibleCards);
      mAdapter = new MyAdapter(visibleCards, this, gridMode);
      mRecyclerView.setAdapter(mAdapter);
      restoreCollectionScrollAfterDetail();
    }
    if (mPersistorMode.equals("1"))//newdeck
    {
      mAdapter = new MyAdapter(mBiblio.cards, this, false);
      mRecyclerView.setAdapter(mAdapter);
    }
    if (mPersistorMode.equals("2"))//Editdeck
    {
      mAdapter = new MyAdapter(mBiblio.cards, this, false);
      mRecyclerView.setAdapter(mAdapter);
    }
  }

  private void sortCards(List<CardInfo> cards) {
    switch (currentSortMode) {
      case 1:
        Collections.sort(cards, new Comparator<CardInfo>() {
          @Override public int compare(CardInfo left, CardInfo right) {
            return safe(left.getName()).compareToIgnoreCase(safe(right.getName()));
          }
        });
        break;
      case 2:
        Collections.sort(cards, new Comparator<CardInfo>() {
          @Override public int compare(CardInfo left, CardInfo right) {
            return Double.compare(parseCardPrice(right), parseCardPrice(left));
          }
        });
        break;
      case 3:
        Collections.sort(cards, new Comparator<CardInfo>() {
          @Override public int compare(CardInfo left, CardInfo right) {
            String leftSet = safe(left.getSetName()) + " " + safe(left.getSetCode());
            String rightSet = safe(right.getSetName()) + " " + safe(right.getSetCode());
            int bySet = leftSet.compareToIgnoreCase(rightSet);
            if (bySet != 0) return bySet;
            int byCollector = collectorSortKey(left).compareTo(collectorSortKey(right));
            return byCollector != 0 ? byCollector : safe(left.getName()).compareToIgnoreCase(safe(right.getName()));
          }
        });
        break;
      default:
        Collections.sort(cards, new Comparator<CardInfo>() {
          @Override public int compare(CardInfo left, CardInfo right) {
            return Long.compare(left.getAddedAt(), right.getAddedAt());
          }
        });
    }
  }

  private String collectorSortKey(CardInfo card) {
    String raw = safe(card.getCollectorNumber()).trim().toLowerCase(Locale.ROOT);
    int split = 0;
    while (split < raw.length() && Character.isDigit(raw.charAt(split))) split++;
    if (split == 0) return "~~~~~~~~~~~~" + raw;
    String digits = raw.substring(0, split);
    StringBuilder padded = new StringBuilder();
    for (int index = digits.length(); index < 12; index++) padded.append('0');
    return padded.append(digits).append(raw.substring(split)).toString();
  }

  private void showDeckSummaries() {
    DeckCatalog catalog = DeckCatalogStore.load(this, mBiblio);
    List<DeckSummaryAdapter.Summary> summaries = new ArrayList<>();
    for (DeckDefinition deck : catalog.decks) {
      List<CardInfo> members = new ArrayList<>();
      int mainCount = 0;
      int sideboardCount = 0;
      for (CardInfo card : mBiblio.cards) {
        if (!card.getDecks().contains(deck.getName())) continue;
        members.add(card);
        if (card.isSideboardForDeck(deck.getName())) sideboardCount += card.getQuantityCount();
        else mainCount += card.getQuantityCount();
      }
      Collections.sort(members, (left, right) -> Long.compare(left.getAddedAt(), right.getAddedAt()));
      summaries.add(new DeckSummaryAdapter.Summary(
          deck, members.isEmpty() ? null : members.get(0), mainCount, sideboardCount));
    }
    Collections.sort(summaries, (left, right) ->
        Long.compare(left.deck.getCreatedAt(), right.deck.getCreatedAt()));
    totalText.setText(getResources().getQuantityString(
        R.plurals.deck_count, summaries.size(), summaries.size()));
    mAdapter = new DeckSummaryAdapter(
        summaries,
        gridMode,
        new DeckSummaryAdapter.Listener() {
          @Override public void onOpen(DeckDefinition deck) {
            currentFilterKey = "deck:" + deck.getName();
            refreshUI();
          }

          @Override public void onEdit(DeckDefinition deck) {
            openDeckBuilder(deck);
          }
        },
        Typeface.createFromAsset(getAssets(), "title_font.ttf"));
    mRecyclerView.setAdapter(mAdapter);
  }

  /** Keeps different printings of one name adjacent without losing the selected group order. */
  private List<CardInfo> clusterSameNamedCards(List<CardInfo> cards) {
    LinkedHashMap<String, List<CardInfo>> groups = new LinkedHashMap<>();
    for (CardInfo card : cards) {
      String key = normalizeForFilter(card.getName());
      List<CardInfo> named = groups.get(key);
      if (named == null) {
        named = new ArrayList<>();
        groups.put(key, named);
      }
      named.add(card);
    }
    List<CardInfo> result = new ArrayList<>();
    for (List<CardInfo> named : groups.values()) result.addAll(named);
    return result;
  }

  /** Legacy files may contain one row per scan; identical printing/finish rows become one copy badge. */
  private boolean consolidateIdenticalCopies() {
    if (mBiblio == null || mBiblio.cards == null) return false;
    LinkedHashMap<String, CardInfo> representatives = new LinkedHashMap<>();
    List<CardInfo> merged = new ArrayList<>();
    boolean changed = false;
    for (CardInfo card : mBiblio.cards) {
      String key = normalizeForFilter(card.getName()) + "|" + safe(card.getPrintingUuid()).trim()
          + "|" + safe(card.getSetCode()).trim().toLowerCase(Locale.ROOT)
          + "|" + safe(card.getFinish()).trim().toLowerCase(Locale.ROOT)
          + "|" + card.getCondition();
      CardInfo representative = representatives.get(key);
      if (representative == null) {
        representatives.put(key, card);
        merged.add(card);
      } else {
        representative.setQuantityCount(representative.getQuantityCount() + card.getQuantityCount());
        for (String group : card.getGroups()) representative.addGroup(group);
        for (String deck : card.getDecks()) representative.setDeckZone(deck, card.isSideboardForDeck(deck));
        changed = true;
      }
    }
    if (changed) {
      mBiblio.cards.clear();
      mBiblio.cards.addAll(merged);
    }
    return changed;
  }

  private double parseCardPrice(CardInfo card) {
    String raw = card.getPriceM();
    if (raw == null || raw.trim().length() == 0) {
      raw = card.getPrice();
    } else {
      try {
        return CardCondition.adjustedAmount(Double.parseDouble(raw.trim()), card.getCondition());
      } catch (NumberFormatException ignored) { }
    }
    if (raw == null) return 0d;
    try {
      String cleaned = raw.replaceAll("[^0-9,.-]", "").replace(',', '.');
      return cleaned.length() == 0 ? 0d : Double.parseDouble(cleaned);
    } catch (Exception ignored) {
      return 0d;
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private boolean matchesCurrentFilter(CardInfo card) {
    String query = normalizeForFilter(currentTextFilter);
    if (query.length() > 0) {
      String searchable = normalizeForFilter(
          safe(card.getName()) + " " + safe(card.getSetName()) + " " + safe(card.getSetCode()));
      if (!searchable.contains(query)) return false;
    }
    if ("all".equals(currentFilterKey)) return true;
    if (currentFilterKey.startsWith("set:")) {
      return setFilterKey(card).equals(currentFilterKey);
    }
    if (currentFilterKey.startsWith("group:")) {
      return card.getGroups().contains(currentFilterKey.substring("group:".length()));
    }
    if (currentFilterKey.startsWith("deck:")) {
      return card.getDecks().contains(currentFilterKey.substring("deck:".length()));
    }
    return true;
  }

  private String normalizeForFilter(String value) {
    String normalized = java.text.Normalizer.normalize(safe(value), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "");
    return normalized.toLowerCase(Locale.ROOT).trim();
  }

  private void updateFilterOptions() {
    if (filterSpinner == null || mBiblio == null) return;
    filterOptions.clear();
    filterOptions.add(new GroupFilterOption("all", getString(
        currentSection == SECTION_GROUPS ? R.string.all_decks : R.string.all_cards)));
    if (currentSection == SECTION_SETS) {
      filterLabel.setText(R.string.expansion_filter_label);
      Map<String, FilterCount> sets = new LinkedHashMap<>();
      List<CardInfo> sorted = new ArrayList<>(mBiblio.cards);
      Collections.sort(sorted,
          (left, right) -> setDisplayName(left).compareToIgnoreCase(setDisplayName(right)));
      for (CardInfo card : sorted) {
        String key = setFilterKey(card);
        if ("set:".equals(key)) continue;
        FilterCount count = sets.get(key);
        if (count == null) {
          count = new FilterCount(setDisplayName(card));
          sets.put(key, count);
        }
        count.count += card.getQuantityCount();
      }
      for (Map.Entry<String, FilterCount> entry : sets.entrySet()) {
        FilterCount count = entry.getValue();
        filterOptions.add(new GroupFilterOption(entry.getKey(),
            getString(R.string.filter_with_count, count.label, count.count)));
      }
      boolean hasRememberedSet = false;
      for (GroupFilterOption option : filterOptions) {
        if (option.key.equals(currentFilterKey) && option.key.startsWith("set:")) hasRememberedSet = true;
      }
      if (!hasRememberedSet && filterOptions.size() > 1) currentFilterKey = filterOptions.get(1).key;
    } else if (currentSection == SECTION_GROUPS) {
      filterLabel.setText(R.string.deck_filter_label);
      appendGroupFilterOptions(true);
    } else {
      filterLabel.setText(R.string.filter_cards);
    }
    int selectedPosition = 0;
    for (int index = 0; index < filterOptions.size(); index++) {
      if (filterOptions.get(index).key.equals(currentFilterKey)) selectedPosition = index;
    }
    if (selectedPosition == 0 && !"all".equals(currentFilterKey)) currentFilterKey = "all";
    updatingFilterSpinner = true;
    ArrayAdapter<GroupFilterOption> adapter = new ArrayAdapter<>(
        this, R.layout.spinner_item, filterOptions);
    adapter.setDropDownViewResource(R.layout.spinner_item);
    filterSpinner.setAdapter(adapter);
    filterSpinner.setSelection(selectedPosition, false);
    updatingFilterSpinner = false;
  }

  private void promptForDeckCreation(final CardInfo initialCard) {
    int padding = (int) (16 * getResources().getDisplayMetrics().density);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(padding, padding / 2, padding, 0);
    final EditText input = new EditText(this);
    input.setHint(R.string.deck_name);
    final Spinner formats = new Spinner(this);
    final TextView rules = new TextView(this);
    rules.setPadding(0, padding, 0, padding / 2);
    List<String> labels = new ArrayList<>();
    for (DeckFormatRule rule : DeckFormatRules.INSTANCE.getAll()) labels.add(rule.getLabel());
    ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, labels);
    formatAdapter.setDropDownViewResource(R.layout.spinner_item);
    formats.setAdapter(formatAdapter);
    rules.setText(DeckFormatRules.INSTANCE.getAll().get(0).getSummary());
    formats.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
      DeckFormatRule selected = DeckFormatRules.INSTANCE.getAll().get(position);
      rules.setText(selected.getSummary());
      return kotlin.Unit.INSTANCE;
    }));
    content.addView(input, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    content.addView(formats, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, (int) (48 * getResources().getDisplayMetrics().density)));
    content.addView(rules, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    new AlertDialog.Builder(this)
        .setTitle(R.string.create_deck)
        .setMessage(collectGroupNames(true).isEmpty() ? getString(R.string.create_first_deck) : "")
        .setView(content)
        .setPositiveButton(R.string.continue_label, (dialog, which) -> {
          String name = input.getText().toString().trim();
          if (name.length() == 0) return;
          DeckFormatRule selected = DeckFormatRules.INSTANCE.getAll().get(formats.getSelectedItemPosition());
          DeckDefinition deck = DeckCatalogStore.upsert(this, mBiblio, name, selected.getId());
          if (initialCard == null) openDeckBuilder(deck);
          else showDeckZonePicker(initialCard, deck.getName());
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void openDeckBuilder(DeckDefinition deck) {
    Intent intent = new Intent(this, GroupBuilderActivity.class);
    intent.putExtra(GroupBuilderActivity.EXTRA_DECK_NAME, deck.getName());
    intent.putExtra(GroupBuilderActivity.EXTRA_FORMAT_ID, deck.getFormatId());
    intent.putExtra(GroupBuilderActivity.EXTRA_SORT, currentSortMode);
    intent.putExtra(GroupBuilderActivity.EXTRA_QUERY, currentTextFilter);
    startActivity(intent);
  }

  private void appendGroupFilterOptions(boolean decks) {
    Set<String> names = decks ? new LinkedHashSet<>() : collectGroupNames(false);
    if (decks) {
      for (DeckDefinition definition : DeckCatalogStore.load(this, mBiblio).decks) names.add(definition.getName());
    }
    for (String name : names) {
      int count = 0;
      for (CardInfo card : mBiblio.cards) {
        if ((decks ? card.getDecks() : card.getGroups()).contains(name)) {
          count += card.getQuantityCount();
        }
      }
      String label = getString(decks ? R.string.deck_filter : R.string.group_filter, name);
      filterOptions.add(new GroupFilterOption((decks ? "deck:" : "group:") + name,
          getString(R.string.filter_with_count, label, count)));
    }
  }

  private String setFilterKey(CardInfo card) {
    String code = safe(card.getSetCode()).trim();
    String name = safe(card.getSetName()).trim();
    return "set:" + normalizeForFilter(code.length() > 0 ? code : name);
  }

  private String setDisplayName(CardInfo card) {
    String name = safe(card.getSetName()).trim();
    String code = safe(card.getSetCode()).trim();
    if (name.length() == 0) return code;
    return code.length() == 0 ? name : name + " (" + code + ")";
  }

  private Set<String> collectGroupNames(boolean decks) {
    List<String> names = new ArrayList<>();
    if (mBiblio != null) {
      for (CardInfo card : mBiblio.cards) {
        names.addAll(decks ? card.getDecks() : card.getGroups());
      }
    }
    Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    return new LinkedHashSet<>(names);
  }

  public void showOrganizerDialog(final CardInfo card) {
    final String[] options = {
        getString(R.string.add_to_deck),
        getString(R.string.remove_assignment)
    };
    new AlertDialog.Builder(this)
        .setTitle(R.string.organize_card)
        .setItems(options, (dialog, which) -> {
          if (which == 0) showGroupPicker(card, true);
          if (which == 1) showRemoveAssignmentDialog(card);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showGroupPicker(final CardInfo card, final boolean deck) {
    List<String> existing = new ArrayList<>();
    for (DeckDefinition definition : DeckCatalogStore.load(this, mBiblio).decks) {
      if (!card.getDecks().contains(definition.getName())) existing.add(definition.getName());
    }
    existing.add(getString(R.string.new_deck));
    final String[] names = existing.toArray(new String[0]);
    new AlertDialog.Builder(this)
        .setTitle(R.string.add_to_deck)
        .setItems(names, (dialog, which) -> {
          if (which == names.length - 1) {
            promptForDeckCreation(card);
          } else {
            showDeckZonePicker(card, names[which]);
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showDeckZonePicker(final CardInfo card, final String deckName) {
    DeckDefinition definition = null;
    for (DeckDefinition candidate : DeckCatalogStore.load(this, mBiblio).decks) {
      if (candidate.getName().equalsIgnoreCase(deckName)) definition = candidate;
    }
    if (definition != null && DeckFormatRules.byId(definition.getFormatId()).getMaximumSideboard() == 0) {
      card.setDeckZone(deckName, false);
      saveCollectionAndRefresh();
      Toast.makeText(this, R.string.card_assignment_saved, Toast.LENGTH_SHORT).show();
      return;
    }
    final String[] zones = { getString(R.string.main_deck), getString(R.string.sideboard) };
    new AlertDialog.Builder(this)
        .setTitle(getString(R.string.choose_deck_zone, deckName))
        .setItems(zones, (dialog, which) -> {
          card.setDeckZone(deckName, which == 1);
          saveCollectionAndRefresh();
          Toast.makeText(this, R.string.card_assignment_saved, Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showRemoveAssignmentDialog(final CardInfo card) {
    final List<AssignmentRef> assignments = new ArrayList<>();
    for (String name : card.getDecks()) {
      String zone = getString(card.isSideboardForDeck(name) ? R.string.sideboard : R.string.main_deck);
      assignments.add(new AssignmentRef(name, true, getString(R.string.deck_assignment, name, zone)));
    }
    if (assignments.isEmpty()) {
      Toast.makeText(this, R.string.no_assignments, Toast.LENGTH_SHORT).show();
      return;
    }
    String[] labels = new String[assignments.size()];
    for (int index = 0; index < assignments.size(); index++) labels[index] = assignments.get(index).label;
    new AlertDialog.Builder(this)
        .setTitle(R.string.remove_assignment)
        .setItems(labels, (dialog, which) -> {
          AssignmentRef assignment = assignments.get(which);
          card.removeDeck(assignment.name);
          saveCollectionAndRefresh();
          Toast.makeText(this, R.string.card_assignment_removed, Toast.LENGTH_SHORT).show();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void saveCollectionAndRefresh() {
    DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
    refreshUI();
  }

  public void changeCardQuantity(CardInfo card, int delta) {
    if (card == null || delta == 0) return;
    int current = card.getQuantityCount();
    int next = Math.max(1, current + delta);
    if (next == current) return;
    card.setQuantityCount(next);
    saveCollectionAndRefresh();
  }

  private static class GroupFilterOption {
    final String key;
    final String label;

    GroupFilterOption(String key, String label) {
      this.key = key;
      this.label = label;
    }

    @Override public String toString() {
      return label;
    }
  }

  private static class FilterCount {
    final String label;
    int count;

    FilterCount(String label) {
      this.label = label;
    }
  }

  private static class AssignmentRef {
    final String name;
    final boolean deck;
    final String label;

    AssignmentRef(String name, boolean deck, String label) {
      this.name = name;
      this.deck = deck;
      this.label = label;
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
      if (cardInfo != null) showCardAddedSnackbar(cardInfo);
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
        int currentIndex = findCardInfoInPersistor(cardinfoForUpdate);
        if (currentIndex < 0 && idx != null && idx >= 0 && idx < mBiblio.cards.size()) {
          currentIndex = idx;
        }
        if (currentIndex < 0) return;
        CardInfo current = mBiblio.cards.get(currentIndex);
        String currentPrinting = safe(current.getPrintingUuid()).trim();
        String refreshedPrinting = safe(cardinfoForUpdate.getPrintingUuid()).trim();
        // A detail screen may have added a copy or selected another printing while this older
        // provider request was still running. Merge metadata into the persisted row instead of
        // replacing the whole object (which used to restore the old quantity and lose the +).
        if (currentPrinting.length() == 0 || currentPrinting.equals(refreshedPrinting)) {
          current.setName(cardinfoForUpdate.getName());
          current.setPrice(cardinfoForUpdate.getBasePrice());
          current.setPriceL(cardinfoForUpdate.getPriceL());
          current.setPriceM(cardinfoForUpdate.getPriceM());
          current.setPriceH(cardinfoForUpdate.getPriceH());
          current.setDescription(cardinfoForUpdate.getDescription());
          current.setImgPath(cardinfoForUpdate.getImgPath());
          current.setPrintingUuid(cardinfoForUpdate.getPrintingUuid());
          current.setSetCode(cardinfoForUpdate.getSetCode());
          current.setSetName(cardinfoForUpdate.getSetName());
          current.setCollectorNumber(cardinfoForUpdate.getCollectorNumber());
          current.setFinish(cardinfoForUpdate.getFinish());
          current.lstDescription = cardinfoForUpdate.lstDescription;
        }
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
    int viewId = v.getId();
    if (viewId == R.id.btnOk) {
      submitScannedCard(txtSearch.getText().toString());
    } else if (viewId == R.id.btnCancel) {
      showRecycler();
    } else if (viewId == R.id.fabOcr) {
      showOcr();
    }
  }

  private void submitScannedCard(String scannedName) {
    String normalizedName = scannedName == null ? "" : scannedName.trim();
    if (normalizedName.length() == 0) {
      Snackbar.make(findViewById(R.id.ocrCaptureRoot), R.string.empty_scan_name,
          Snackbar.LENGTH_SHORT).show();
      return;
    }
    doSearch(normalizedName);
    if (closeAfterScanCheck.isChecked()) {
      showRecycler();
    } else {
      prepareScannerForNextCard();
    }
  }

  /** Keeps the camera open and ready while the repository completes metadata in the background. */
  private void prepareScannerForNextCard() {
    hideNamePredictions();
    suppressPredictionWatcher = true;
    txtSearch.setText("");
    suppressPredictionWatcher = false;
    txtSearch.clearFocus();
    InputMethodManager keyboard =
        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    keyboard.hideSoftInputFromWindow(txtSearch.getWindowToken(), 0);
  }

  private void rememberSessionScan(CardInfo card) {
    for (int index = 0; index < scannedSessionCards.size(); index++) {
      CardInfo existing = scannedSessionCards.get(index);
      if (existing.getCollectionItemId().equals(card.getCollectionItemId())) {
        scannedSessionCards.set(index, card);
        updateScanSessionUi();
        return;
      }
    }
    scannedSessionCards.add(card);
    updateScanSessionUi();
  }

  private void updateScanSessionUi() {
    syncSessionCardsFromCollection();
    if (scanSessionButton != null) {
      scanSessionButton.setText(getString(R.string.scan_session_count, scannedSessionCards.size()));
    }
    if (scanSessionAdapter != null) scanSessionAdapter.notifyDataSetChanged();
  }

  /** Rebinds session rows after the collection is reloaded or card detail writes a newer object. */
  private void syncSessionCardsFromCollection() {
    if (mBiblio == null || mBiblio.cards == null || scannedSessionCards.isEmpty()) return;
    Map<String, CardInfo> currentCards = new LinkedHashMap<>();
    for (CardInfo card : mBiblio.cards) currentCards.put(card.getCollectionItemId(), card);
    for (int index = 0; index < scannedSessionCards.size(); index++) {
      CardInfo current = currentCards.get(scannedSessionCards.get(index).getCollectionItemId());
      if (current != null) scannedSessionCards.set(index, current);
    }
  }

  private void showScanSession() {
    updateScanSessionUi();
    if (scannedSessionCards.isEmpty()) {
      new AlertDialog.Builder(this)
          .setTitle(getString(R.string.scan_session_title, 0))
          .setMessage(R.string.scan_session_no_cards)
          .setPositiveButton(android.R.string.ok, null)
          .show();
      return;
    }
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    TextView hint = new TextView(this);
    hint.setText(R.string.scan_session_choose_edition_hint);
    hint.setTextColor(MagicPalette.secondaryColor(this));
    hint.setPadding(dp(18), dp(10), dp(18), dp(8));
    content.addView(hint, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    ListView list = new ListView(this);
    list.setAdapter(scanSessionAdapter);
    list.setMinimumHeight(dp(220));
    content.addView(list, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(getString(R.string.scan_session_title, scannedSessionCards.size()))
        .setView(content)
        .setPositiveButton(R.string.close, null)
        .create();
    list.setOnItemClickListener((parent, view, position, id) -> {
      dialog.dismiss();
      openCardDetails(scanSessionAdapter.getItem(position));
    });
    dialog.show();
  }

  private void showCardConditionPicker(CardInfo sessionCard) {
    String[] labels = getResources().getStringArray(R.array.card_condition_labels);
    String[] codes = CardCondition.codes();
    new AlertDialog.Builder(this)
        .setTitle(R.string.card_condition)
        .setSingleChoiceItems(labels, CardCondition.indexOf(sessionCard.getCondition()),
            (dialog, which) -> {
              CardInfo current = findCollectionCard(sessionCard.getCollectionItemId());
              if (current != null && which >= 0 && which < codes.length) {
                current.setCondition(codes[which]);
                DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
                rememberSessionScan(current);
                updateCardAddedSnackbar(current, false);
                refreshUI();
              }
              dialog.dismiss();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void playScanAcceptedFeedback() {
    try {
      if (scanToneGenerator == null) {
        scanToneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
      }
      scanToneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 180);
    } catch (RuntimeException error) {
      Log.w(TAG, "No se pudo reproducir el sonido de escaneo", error);
    }
    View root = findViewById(R.id.ocrCaptureRoot);
    root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    root.announceForAccessibility(getString(R.string.scan_added_audible));
  }

  private void showCardAddedSnackbar(CardInfo card) {
    rememberSessionScan(card);
    playScanAcceptedFeedback();
    final String cardId = card.getCollectionItemId();
    activeScanCardId = cardId;
    activeScanMetadataFailed = false;
    findViewById(R.id.ocrCaptureRoot).post(() -> {
      if (!cardId.equals(activeScanCardId) || isFinishing() || isDestroyed()) return;
      boolean metadataFailedBeforeDisplay = activeScanMetadataFailed;
      if (activeScanSnackbar != null) activeScanSnackbar.dismiss();
      // Dismissing the previous feedback clears its id in the callback; claim this scan again.
      activeScanCardId = cardId;
      activeScanMetadataFailed = metadataFailedBeforeDisplay;

      Snackbar snackbar = Snackbar.make(
              findViewById(R.id.ocrCaptureRoot), " ", 9000)
          .setBackgroundTint(MagicPalette.primaryVariantColor(this))
          .setTextColor(Color.WHITE)
          .setActionTextColor(MagicPalette.secondaryColor(this));
      ViewGroup snackbarView = (ViewGroup) snackbar.getView();
      View defaultContent = snackbarView.getChildAt(0);
      if (defaultContent != null) defaultContent.setVisibility(View.GONE);

      LinearLayout customContent = new LinearLayout(this);
      customContent.setOrientation(LinearLayout.VERTICAL);
      customContent.setPadding(dp(12), dp(8), dp(8), dp(4));
      LinearLayout summaryRow = new LinearLayout(this);
      summaryRow.setOrientation(LinearLayout.HORIZONTAL);
      summaryRow.setGravity(Gravity.CENTER_VERTICAL);

      TextView message = new TextView(this);
      message.setTextColor(Color.WHITE);
      message.setTextSize(14f);
      message.setMaxLines(3);

      RoundedCardImageView thumbnail = new RoundedCardImageView(this);
      thumbnail.setContentDescription(getString(R.string.added_card_thumbnail));
      thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
      thumbnail.setImageResource(R.drawable.backmtg);
      TextView priceBadge = new TextView(this);
      priceBadge.setTextColor(MagicPalette.secondaryColor(this));
      priceBadge.setTextSize(22f);
      priceBadge.setTypeface(Typeface.DEFAULT_BOLD);
      priceBadge.setGravity(Gravity.CENTER);
      priceBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
      priceBadge.setVisibility(View.GONE);
      LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(48), dp(68));
      imageParams.setMarginEnd(dp(10));
      summaryRow.addView(thumbnail, imageParams);
      LinearLayout textColumn = new LinearLayout(this);
      textColumn.setOrientation(LinearLayout.VERTICAL);
      textColumn.addView(message, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      textColumn.addView(priceBadge, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      summaryRow.addView(textColumn, new LinearLayout.LayoutParams(0,
          LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      customContent.addView(summaryRow, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView viewCardAction = new TextView(this);
      viewCardAction.setText(R.string.view_card);
      viewCardAction.setTextColor(MagicPalette.secondaryColor(this));
      viewCardAction.setTextSize(14f);
      viewCardAction.setTypeface(Typeface.DEFAULT_BOLD);
      viewCardAction.setGravity(Gravity.CENTER);
      viewCardAction.setMinHeight(dp(44));
      viewCardAction.setPadding(dp(18), 0, dp(18), 0);
      viewCardAction.setContentDescription(getString(R.string.view_card));
      viewCardAction.setOnClickListener(view -> {
        snackbar.dismiss();
        openCardDetails(card);
      });
      LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      actionParams.gravity = Gravity.END;
      customContent.addView(viewCardAction, actionParams);
      snackbarView.addView(customContent, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      activeScanSnackbar = snackbar;
      activeScanThumbnail = thumbnail;
      activeScanMessage = message;
      activeScanPrice = priceBadge;
      snackbar.addCallback(new Snackbar.Callback() {
        @Override public void onDismissed(Snackbar dismissed, int event) {
          if (activeScanSnackbar != dismissed) return;
          activeScanSnackbar = null;
          activeScanThumbnail = null;
          activeScanMessage = null;
          activeScanPrice = null;
          activeScanCardId = null;
          activeScanMetadataFailed = false;
        }
      });
      updateCardAddedSnackbar(card, activeScanMetadataFailed);
      snackbar.show();
    });
  }

  private void updateCardAddedSnackbar(CardInfo card, boolean metadataFailed) {
    if (card == null) return;
    updateScanSessionUi();
    if (activeScanCardId == null ||
        !activeScanCardId.equals(card.getCollectionItemId())) return;
    activeScanMetadataFailed = metadataFailed;
    if (activeScanMessage != null) {
      activeScanMessage.setText(scanFeedbackText(card, metadataFailed));
    }
    if (activeScanThumbnail != null && safe(card.getImgPath()).trim().length() > 0) {
      CardImageCache.displayKeepingCurrent(this, card.getImgPath(), activeScanThumbnail);
    }
    if (activeScanPrice != null) {
      String price = safe(card.getPrice()).trim();
      activeScanPrice.setText(price);
      activeScanPrice.setVisibility(price.length() == 0 ? View.GONE : View.VISIBLE);
    }
  }

  private String scanFeedbackText(CardInfo card, boolean metadataFailed) {
    String name = safe(card.getName()).trim();
    if (metadataFailed) return getString(R.string.card_added_metadata_error, name);

    List<String> metadata = new ArrayList<>();
    String setName = safe(card.getSetName()).trim();
    String setCode = safe(card.getSetCode()).trim();
    if (setName.length() > 0 && setCode.length() > 0) metadata.add(setName + " (" + setCode + ")");
    else if (setName.length() > 0) metadata.add(setName);
    else if (setCode.length() > 0) metadata.add(setCode);
    if (safe(card.getCollectorNumber()).trim().length() > 0) {
      metadata.add("#" + card.getCollectorNumber().trim());
    }
    if (safe(card.getFinish()).trim().length() > 0) {
      metadata.add(getString("foil".equalsIgnoreCase(card.getFinish())
          ? R.string.foil : R.string.nonfoil));
    }
    boolean metadataReady = safe(card.getPrintingUuid()).trim().length() > 0 ||
        safe(card.getImgPath()).trim().length() > 0 || !metadata.isEmpty();
    if (!metadataReady) return getString(R.string.card_added_loading, name);
    metadata.add(getResources().getStringArray(R.array.card_condition_labels)[
        CardCondition.indexOf(card.getCondition())]);
    String details = metadata.isEmpty()
        ? getString(R.string.card_metadata_updated)
        : TextUtils.join(" · ", metadata);
    return getString(R.string.card_added_ready, name, details);
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  /** Opens a card without losing the exact row/offset currently visible in the collection. */
  public void openCardDetails(CardInfo card) {
    rememberCollectionScrollForDetail();
    cardDetailOpen = true;
    Intent intent = new Intent(this, Main2Activity.class);
    intent.putExtra(Main2Activity.EXTRA_CARD_NAME, card.getName());
    intent.putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID, card.getCollectionItemId());
    startActivity(intent);
  }

  private void rememberCollectionScrollForDetail() {
    if (!(mLayoutManager instanceof LinearLayoutManager) || !(mAdapter instanceof MyAdapter)) return;
    LinearLayoutManager layout = (LinearLayoutManager) mLayoutManager;
    int position = layout.findFirstVisibleItemPosition();
    if (position == RecyclerView.NO_POSITION || position >= mAdapter.getItemCount()) return;
    CardInfo anchor = ((MyAdapter) mAdapter).getItem(position);
    View anchorView = layout.findViewByPosition(position);
    pendingDetailScrollItemId = anchor.getCollectionItemId();
    pendingDetailScrollOffset = anchorView == null
        ? 0
        : layout.getDecoratedTop(anchorView) - mRecyclerView.getPaddingTop();
  }

  private void restoreCollectionScrollAfterDetail() {
    if (pendingDetailScrollItemId == null || !(mLayoutManager instanceof LinearLayoutManager) ||
        !(mAdapter instanceof MyAdapter)) return;
    final String anchorId = pendingDetailScrollItemId;
    final int anchorOffset = pendingDetailScrollOffset;
    final int position = ((MyAdapter) mAdapter).indexOfCollectionItem(anchorId);
    if (position < 0) return;
    mRecyclerView.post(() -> {
      if (mLayoutManager instanceof LinearLayoutManager) {
        ((LinearLayoutManager) mLayoutManager).scrollToPositionWithOffset(position, anchorOffset);
      }
    });
  }

  public void doSearch(String searchText) {
    //save and show
    CardInfo cardinfo = new CardInfo(searchText, "", "", "", "");
    persistInfo(cardinfo);

    requestCardInfo(cardinfo, getIdxCardInfoInPersistor(), false);
  }

  public void refreshCard(CardInfo cardInfo) {
    int persistorIndex = findCardInfoInPersistor(cardInfo);
    if (persistorIndex >= 0) {
      requestCardInfo(cardInfo, persistorIndex, true);
    }
  }

  public void deleteCardFromCollection(CardInfo cardInfo) {
    if (mBiblio == null || cardInfo == null) return;
    String collectionItemId = cardInfo.getCollectionItemId();
    for (int index = mBiblio.cards.size() - 1; index >= 0; index--) {
      if (collectionItemId.equals(mBiblio.cards.get(index).getCollectionItemId())) {
        mBiblio.cards.remove(index);
        break;
      }
    }
    DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
    refreshUI();
  }

  private int findCardInfoInPersistor(CardInfo cardInfo) {
    if (mPersistorMode.equals("0") && mBiblio != null) {
      String collectionItemId = cardInfo.getCollectionItemId();
      for (int index = 0; index < mBiblio.cards.size(); index++) {
        if (collectionItemId.equals(mBiblio.cards.get(index).getCollectionItemId())) return index;
      }
    }
    return -1;
  }

  private CardInfo findCollectionCard(String collectionItemId) {
    if (!"0".equals(mPersistorMode) || mBiblio == null || collectionItemId == null) return null;
    for (CardInfo card : mBiblio.cards) {
      if (collectionItemId.equals(card.getCollectionItemId())) return card;
    }
    return null;
  }

  private void requestCardInfo(CardInfo cardinfo, int persistorIndex, boolean forcePriceRefresh) {

    //region create objectGetterCardinfo
    mLstHandlers.add(myHandler);
    int myIdx = mLstHandlers.size() - 1;
    mIdxCardInfoInLstCInfo.add(myIdx);
    mLstCardInfo.add(cardinfo);
    //MtgDataprovider
    IDataProvider myDataProvider =
        new MtgJsonRoomDataProvider(this, mLstHandlers.get(myIdx), myIdx, forcePriceRefresh);
    mLstDataProviders.add(myDataProvider);
    mIdxInPersistor.add(persistorIndex);

    // mLstHandlers.set(myIdx, myHandler); String query =  );
    mLstDataProviders.get(myIdx)
        .GetCardInfo(Uri.encode(cardinfo.getName()), mLstCardInfo.get(myIdx));
  }

  private void showOcr() {
    hideNamePredictions();
    txtSearch.setText("");
    lytRecycler.setVisibility(View.GONE);
    settingsPlaceholder.setVisibility(View.GONE);
    if (bottomNavigation != null) bottomNavigation.setVisibility(View.GONE);
    fabOcr.setVisibility(View.GONE);
    topLayout.setVisibility(View.VISIBLE);
    lytSearch.setVisibility(View.VISIBLE);
    lytSearch.bringToFront();
    scanStability.resetPending();
    scanInProgress = false;
    cardScanGuide.setMessage(getString(R.string.scan_align_card));
  }

  private void showRecycler() {
    hideNamePredictions();

    if (bottomNavigation != null) bottomNavigation.setVisibility(View.VISIBLE);
    showSelectedSection();
    topLayout.setVisibility(View.GONE);
    lytSearch.setVisibility(View.GONE);
    scanStability.resetPending();
    scanInProgress = false;
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
