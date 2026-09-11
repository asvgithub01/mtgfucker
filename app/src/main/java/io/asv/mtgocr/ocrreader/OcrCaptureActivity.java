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
import android.widget.AdapterView;
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
import io.asv.mtgocr.ocrreader.data.CardImageVariant;
import io.asv.mtgocr.ocrreader.data.CardLanguage;
import io.asv.mtgocr.ocrreader.data.LocalCardNameMatch;
import io.asv.mtgocr.ocrreader.data.CardNameSuggestion;
import io.asv.mtgocr.ocrreader.data.DeckCatalogStore;
import io.asv.mtgocr.ocrreader.data.IDataProvider;
import io.asv.mtgocr.ocrreader.data.MtgJsonRoomDataProvider;
import io.asv.mtgocr.ocrreader.data.MagicSetOption;
import io.asv.mtgocr.ocrreader.data.PriceSourcePreferences;
import io.asv.mtgocr.ocrreader.data.PriceCurrency;
import io.asv.mtgocr.ocrreader.data.PhotoCardNameMatch;
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
import java.util.ArrayDeque;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
  private static final int RC_PICK_CARD_PHOTO = 3;
  private static final String SCANNER_PREFERENCES = "scanner_preferences";
  private static final String PREF_CLOSE_AFTER_SCAN = "close_after_successful_scan";
  private static final String PREF_AUTO_IDENTIFY = "auto_identify";
  private static final String PREF_QUICK_SCAN = "quick_scan";
  private static final String PREF_LOCKED_SET = "locked_set";
  private static final String PREF_ASK_EDITION_AFTER_SCAN = "ask_edition_after_scan";
  private static final String PREF_ONLY_OWNED_SETS = "only_owned_sets";
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
  private TextView scanDebugStatus;
  private TextView scanOcrCharacters;
  private View scanIndexPreparation;
  private ProgressBar scanIndexPreparationProgress;
  private TextView scanIndexPreparationText;
  private Button scanSessionButton;
  private TextView scanSessionTotalText;
  FloatingActionButton fabOcr, fabOcrMlKit;
  EditText txtSearch;
  RelativeLayout lytSearch;
  LinearLayout lytRecycler, topLayout;
  private Spinner sortSpinner, filterSpinner;
  private TextView filterLabel;
  private ArcaneGlassLayout collectionControls;
  private ArcaneGlassLayout setCatalogControls;
  private ArcaneGlassLayout photoControls;
  private TextView totalText;
  private TextView setCatalogStatus;
  private ProgressBar setCatalogProgress;
  private EditText setCatalogSearch;
  private CheckBox ownedSetCatalogCheck;
  private MagicSetCatalogAdapter setCatalogAdapter;
  private PhotoScanLibraryAdapter photoScanAdapter;
  private TextView photoLibraryStatus;
  private final Set<String> activePhotoAnalyses = new LinkedHashSet<>();
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
  private static final int SECTION_PHOTOS = 5;
  private int currentSection = SECTION_LIBRARY;
  private final Handler autoOcrHandler = new Handler(Looper.getMainLooper());
  private final ExecutorService collectionSaveExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService sessionPriceExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService photoImportExecutor = Executors.newSingleThreadExecutor();
  private Runnable pendingCollectionSave;
  private Runnable pendingSessionPriceUpdate;
  private int sessionPriceUpdateGeneration;
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
  private boolean scanSnackbarScheduled;
  private RoundedCardImageView activeScanThumbnail;
  private TextView activeScanMessage;
  private TextView activeScanPrice;
  private TextView activeScanQuantity;
  private TextView activeScanDecreaseQuantity;
  private CheckBox activeScanFoil;
  private final List<CardInfo> scannedSessionCards = new ArrayList<>();
  private final Set<String> selectedSessionCardIds = new LinkedHashSet<>();
  private final ScanSessionRefreshCoordinator sessionRefreshCoordinator =
      new ScanSessionRefreshCoordinator();
  private CheckBox selectAllSessionCards;
  private TextView sessionSelectionCount;
  private Button createSessionGroupButton;
  private boolean updatingSessionSelection;
  private final ArrayDeque<CardInfo> readyScanNotifications = new ArrayDeque<>();
  private final Set<String> scanMetadataLoadingIds = new LinkedHashSet<>();
  private final Set<String> scanMetadataFailedIds = new LinkedHashSet<>();
  private final Set<String> scanReadyNotifiedIds = new LinkedHashSet<>();
  private final Set<String> scanLanguageLoadingIds = new LinkedHashSet<>();
  private final Set<String> scanLanguageReselectAttemptedIds = new LinkedHashSet<>();
  private ScanSessionAdapter scanSessionAdapter;
  private AlertDialog scanSessionDialog;
  private ListView scanSessionList;
  private boolean scanSessionOpen;
  private boolean reopenScanSessionAfterDetail;
  private int pendingSessionScrollPosition;
  private int pendingSessionScrollOffset;
  private int currentSessionSortMode = ScanSessionSort.ENTRY;
  private String activeScanGroupName = "";
  private String sessionSourceGroupName = "";
  private ToneGenerator scanToneGenerator;
  private final CardScanStability scanStability = new CardScanStability(1, 1_800L);
  private boolean scanLookupInFlight;
  private boolean scanInProgress;
  private long lastOcrLookupAt;
  private long scanLookupStartedAt;
  private long scanWorkStartedAt;
  private int scanLookupBlockedFrames;
  private int scanWorkBlockedFrames;
  private String scanWorkDebugLabel = "";
  private Runnable pendingScanDebugHide;
  private long lastOcrMissLoggedAt;
  private long lastOcrCharactersAt;
  private String lastOcrCharacters = "";
  private double lastDisplayedSessionTotal;
  // The maintained scanner is ML Kit Japanese/Latin. Starting with it also avoids destroying the
  // legacy detector and cold-creating ML Kit at the exact moment the user opens the scanner.
  private boolean useMlKitJapaneseOcr = true;
  private boolean nameIndexPreparing;
  private boolean nameIndexReady;
  private Runnable pendingNameIndexReadyHide;
  private boolean firstResume = true;

  /**
   * Initializes the UI and creates the detector pipeline.
   */
  @Override public void onCreate(Bundle icicle) {
    MagicPalette.applyTheme(this);
    super.onCreate(icicle);
    if (icicle != null) currentSection = icicle.getInt(STATE_SECTION, SECTION_LIBRARY);
    // Expansiones and Colecciones now share the complete collection catalog.
    if (currentSection == SECTION_SETS) currentSection = SECTION_CATALOG;
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
    scanDebugStatus = (TextView) findViewById(R.id.txtScanDebugStatus);
    scanOcrCharacters = (TextView) findViewById(R.id.txtScanOcrCharacters);
    scanIndexPreparation = findViewById(R.id.scanIndexPreparation);
    scanIndexPreparationProgress = (ProgressBar) findViewById(R.id.scanIndexPreparationProgress);
    scanIndexPreparationText = (TextView) findViewById(R.id.scanIndexPreparationText);
    scanSessionButton = (Button) findViewById(R.id.btnScanSession);
    scanSessionTotalText = (TextView) findViewById(R.id.txtScanSessionTotal);
    fabOcr = (FloatingActionButton) findViewById(R.id.fabOcr);
    fabOcrMlKit = (FloatingActionButton) findViewById(R.id.fabOcrMlKit);
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
    cardRepository.prepareLockedSetOcrAliases(lockedSetCodes());
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
      if (!focused) {
        getSharedPreferences(SCANNER_PREFERENCES, MODE_PRIVATE).edit()
            .putString(PREF_LOCKED_SET, lockedSetInput.getText().toString().trim()).apply();
        cardRepository.prepareLockedSetOcrAliases(lockedSetCodes());
      }
    });
    cardScanGuide.setMessage(getString(R.string.scan_align_card));
    scanSessionAdapter = new ScanSessionAdapter(this, scannedSessionCards,
        new ScanSessionAdapter.Listener() {
          @Override public void onOpen(CardInfo card) { openSessionCardDetails(card); }
          @Override public void onImage(CardInfo card) { openSessionCardGallery(card); }
          @Override public void onCondition(CardInfo card) { showCardConditionPicker(card); }
          @Override public void onFoil(CardInfo card, boolean foil) {
            adjustScannedCardFoil(card.getCollectionItemId(), foil);
          }
          @Override public void onIncreaseQuantity(CardInfo card) { increaseSessionCardQuantity(card); }
          @Override public void onDecreaseQuantity(CardInfo card) { decreaseSessionCardQuantity(card); }
          @Override public void onRefresh(CardInfo card) { retrySessionCard(card, true); }
          @Override public void onDelete(CardInfo card) { deleteSessionCard(card); }
          @Override public void onSelection(CardInfo card, boolean selected) {
            if (selected) selectedSessionCardIds.add(card.getCollectionItemId());
            else selectedSessionCardIds.remove(card.getCollectionItemId());
            updateSessionSelectionUi();
          }
          @Override public boolean isSelected(CardInfo card) {
            return selectedSessionCardIds.contains(card.getCollectionItemId());
          }
          @Override public boolean isLoading(CardInfo card) {
            return scanMetadataLoadingIds.contains(card.getCollectionItemId());
          }
        });
    scanSessionButton.setOnClickListener(view -> showScanSession());
    scanToneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
    fabOcr.setOnClickListener(this);
    fabOcrMlKit.setOnClickListener(this);
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
    setUpPhotos();
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
    cardRepository.preparePriceIndex(ready -> kotlin.Unit.INSTANCE);
  }

  /** Builds the large multilingual name index only when the scanner is actually requested. */
  private void prepareNameIndexForScanner() {
    if (nameIndexReady || nameIndexPreparing) {
      renderNameIndexPreparation();
      return;
    }
    nameIndexPreparing = true;
    renderNameIndexPreparation();
    cardRepository.prepareCardNamePredictor(ready -> {
      if (isFinishing() || isDestroyed()) return kotlin.Unit.INSTANCE;
      nameIndexPreparing = false;
      nameIndexReady = ready;
      if (ready) {
        scanIndexPreparationProgress.setVisibility(View.GONE);
        scanIndexPreparationText.setText(R.string.scan_name_index_ready);
        scanIndexPreparation.setVisibility(View.VISIBLE);
        scanIndexPreparation.announceForAccessibility(
            getString(R.string.scan_name_index_ready));
        if (pendingNameIndexReadyHide != null) {
          autoOcrHandler.removeCallbacks(pendingNameIndexReadyHide);
        }
        pendingNameIndexReadyHide = () -> {
          pendingNameIndexReadyHide = null;
          scanIndexPreparation.setVisibility(View.GONE);
          if (isScannerReaderActive()) {
            cardScanGuide.setMessage(getString(R.string.scan_align_card));
          }
        };
        autoOcrHandler.postDelayed(pendingNameIndexReadyHide, 650L);
      } else {
        scanIndexPreparation.setVisibility(View.GONE);
      }
      return kotlin.Unit.INSTANCE;
    });
  }

  private void renderNameIndexPreparation() {
    if (!nameIndexPreparing || !isScannerReaderActive()) {
      if (!nameIndexReady) scanIndexPreparation.setVisibility(View.GONE);
      return;
    }
    scanIndexPreparationProgress.setVisibility(View.VISIBLE);
    scanIndexPreparationText.setText(R.string.scan_preparing_name_index);
    scanIndexPreparation.setVisibility(View.VISIBLE);
    scanIndexPreparation.bringToFront();
    cardScanGuide.setMessage(getString(R.string.scan_preparing_name_index));
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
    renderOcrCharacters(candidates);
    if (autoIdentifyCheck == null || !autoIdentifyCheck.isChecked() ||
        !isScannerReaderActive()) return;
    if (nameIndexPreparing) return;
    if (scanLookupInFlight) {
      reportScannerGateBlocked(
          getString(R.string.scan_debug_name_lookup), scanLookupStartedAt,
          ++scanLookupBlockedFrames);
      return;
    }
    if (scanInProgress) {
      reportScannerGateBlocked(scanWorkDebugLabel, scanWorkStartedAt, ++scanWorkBlockedFrames);
      return;
    }
    long now = SystemClock.elapsedRealtime();
    if (now - lastOcrLookupAt < 120L) return;
    lastOcrLookupAt = now;
    scanLookupInFlight = true;
    scanLookupStartedAt = now;
    scanLookupBlockedFrames = 0;
    cardRepository.matchLocalOcrText(candidates, lockedSetCodes(), match -> {
      finishScannerLookupGate();
      if (match == null) {
        logOcrMiss(candidates);
        return kotlin.Unit.INSTANCE;
      }
      if (!isScannerReaderActive() || scanInProgress) return kotlin.Unit.INSTANCE;
      String displayName = match.getDisplayName();
      cardScanGuide.setMessage(getString(R.string.scan_reading_name, displayName));
      suppressPredictionWatcher = true;
      txtSearch.setText(displayName);
      suppressPredictionWatcher = false;
      if (scanStability.observe(match.getCanonicalName(), SystemClock.elapsedRealtime())) {
        Log.i(TAG, "SCAN_OCR accepted name=" + match.getCanonicalName() +
            " language=" + match.getLanguage());
        playOcrRecognizedFeedback();
        captureArtworkForIdentification(match);
      }
      return kotlin.Unit.INSTANCE;
    });
  }

  private void renderOcrCharacters(List<String> candidates) {
    if (scanOcrCharacters == null || candidates == null || candidates.isEmpty()) return;
    int shown = Math.min(4, candidates.size());
    String raw = TextUtils.join(" | ", candidates.subList(0, shown));
    if (raw.length() > 180) raw = raw.substring(0, 177) + "…";
    if (raw.equals(lastOcrCharacters)) return;
    long now = SystemClock.elapsedRealtime();
    if (now - lastOcrCharactersAt < 120L) return;
    lastOcrCharactersAt = now;
    lastOcrCharacters = raw;
    scanOcrCharacters.setText(getString(R.string.scan_debug_characters, raw));
    Log.d(TAG, "SCAN_OCR raw=" + raw);
  }

  private void logOcrMiss(List<String> candidates) {
    long now = SystemClock.elapsedRealtime();
    if (now - lastOcrMissLoggedAt < 1_000L) return;
    lastOcrMissLoggedAt = now;
    int shown = Math.min(3, candidates.size());
    Log.d(TAG, "SCAN_OCR no_match candidates=" + candidates.subList(0, shown));
  }

  private boolean isScannerReaderActive() {
    return lytSearch != null && lytSearch.getVisibility() == View.VISIBLE &&
        !scanSessionOpen && !cardDetailOpen && !isFinishing() && !isDestroyed();
  }

  private void beginScannerWork(String label) {
    scanInProgress = true;
    scanWorkStartedAt = SystemClock.elapsedRealtime();
    scanWorkBlockedFrames = 0;
    scanWorkDebugLabel = label == null ? "" : label;
  }

  private void finishScannerLookupGate() {
    scanLookupInFlight = false;
    finishScannerGate(
        getString(R.string.scan_debug_name_lookup), scanLookupStartedAt, scanLookupBlockedFrames);
    scanLookupStartedAt = 0L;
    scanLookupBlockedFrames = 0;
  }

  private void finishScannerWorkGate() {
    scanInProgress = false;
    finishScannerGate(scanWorkDebugLabel, scanWorkStartedAt, scanWorkBlockedFrames);
    scanWorkStartedAt = 0L;
    scanWorkBlockedFrames = 0;
    scanWorkDebugLabel = "";
  }

  private void reportScannerGateBlocked(String label, long startedAt, int blockedFrames) {
    if (scanDebugStatus == null || !isScannerReaderActive()) return;
    long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    if (elapsed < 350L && blockedFrames < 2) return;
    String safeLabel = TextUtils.isEmpty(label)
        ? getString(R.string.scan_debug_unknown_work) : label;
    scanDebugStatus.setText(getString(
        R.string.scan_debug_blocked, blockedFrames, safeLabel, elapsed));
    scanDebugStatus.setVisibility(View.VISIBLE);
    if (pendingScanDebugHide != null) autoOcrHandler.removeCallbacks(pendingScanDebugHide);
    pendingScanDebugHide = null;
    if (blockedFrames == 1 || blockedFrames % 10 == 0) {
      Log.w(TAG, "SCAN_GATE blocked label=" + safeLabel + " frames=" + blockedFrames +
          " elapsedMs=" + elapsed);
    }
  }

  private void finishScannerGate(String label, long startedAt, int blockedFrames) {
    if (blockedFrames <= 0 || scanDebugStatus == null) return;
    long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
    if (elapsed < 350L && blockedFrames < 2) return;
    String safeLabel = TextUtils.isEmpty(label)
        ? getString(R.string.scan_debug_unknown_work) : label;
    scanDebugStatus.setText(getString(
        R.string.scan_debug_recovered, blockedFrames, safeLabel, elapsed));
    scanDebugStatus.setVisibility(View.VISIBLE);
    Log.i(TAG, "SCAN_GATE recovered label=" + safeLabel + " frames=" + blockedFrames +
        " elapsedMs=" + elapsed);
    if (pendingScanDebugHide != null) autoOcrHandler.removeCallbacks(pendingScanDebugHide);
    pendingScanDebugHide = () -> {
      pendingScanDebugHide = null;
      if (scanDebugStatus != null) scanDebugStatus.setVisibility(View.GONE);
    };
    autoOcrHandler.postDelayed(pendingScanDebugHide, 5_000L);
  }

  private void resetScannerGateDiagnostics() {
    scanLookupInFlight = false;
    scanInProgress = false;
    lastOcrLookupAt = 0L;
    scanLookupStartedAt = 0L;
    scanWorkStartedAt = 0L;
    scanLookupBlockedFrames = 0;
    scanWorkBlockedFrames = 0;
    scanWorkDebugLabel = "";
    if (pendingScanDebugHide != null) autoOcrHandler.removeCallbacks(pendingScanDebugHide);
    pendingScanDebugHide = null;
    if (scanDebugStatus != null) scanDebugStatus.setVisibility(View.GONE);
  }

  private void captureArtworkForIdentification(LocalCardNameMatch match) {
    if (mCameraSource == null || scanInProgress || !isScannerReaderActive()) return;
    beginScannerWork(quickScanCheck.isChecked()
        ? getString(R.string.scan_debug_card_info)
        : getString(R.string.scan_debug_artwork));
    if (quickScanCheck.isChecked()) {
      cardScanGuide.setMessage(getString(R.string.scan_reading_name, match.getDisplayName()));
      cardRepository.quickScanCard(match.getCanonicalName(), lockedSetCodes(), (option, error) -> {
        finishScannerWorkGate();
        if (!isScannerReaderActive()) {
        } else if (error != null) {
          if (lockedSetCodes().isEmpty()) {
            submitScannedCard(match.getDisplayName(), match.getLanguage());
          } else {
            cardScanGuide.setMessage(getString(R.string.scan_no_set_match));
          }
        } else if (option == null) {
          submitScannedCard(match.getDisplayName(), match.getLanguage());
        } else {
          addIdentifiedPrinting(option, match.getLanguage());
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
      finishScannerWorkGate();
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
    if (!isScannerReaderActive()) {
      finishScannerWorkGate();
      return;
    }
    List<CardIdentificationCandidate> candidates = result.getCandidates();
    if (error != null || candidates.isEmpty()) {
      Log.w(TAG, "No se pudo resolver la impresión por ilustración", error);
      finishScannerWorkGate();
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
      if (preferred != null) addIdentifiedPrinting(preferred, nameMatch.getLanguage());
      else {
        finishScannerWorkGate();
        cardScanGuide.setMessage(getString(R.string.scan_identification_failed));
      }
      return;
    }
    if (candidates.size() == 1) {
      addIdentifiedPrinting(candidates.get(0).getOption(), nameMatch.getLanguage());
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
        .setItems(labels, (dialog, which) ->
            addIdentifiedPrinting(candidates.get(which).getOption(), nameMatch.getLanguage()))
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
          finishScannerWorkGate();
          scanStability.allowRepeat();
          cardScanGuide.setMessage(getString(R.string.scan_align_card));
        })
        .setOnCancelListener(dialog -> {
          finishScannerWorkGate();
          scanStability.allowRepeat();
          cardScanGuide.setMessage(getString(R.string.scan_align_card));
        })
        .show();
  }

  private void addIdentifiedPrinting(CardEditionOption option, String detectedLanguage) {
    // Do this before touching the collection: its serialization/UI must not stall the camera gate.
    finishScannerWorkGate();
    persistIdentifiedPrinting(option, detectedLanguage);
    if (closeAfterScanCheck.isChecked()) {
      showRecycler();
    } else {
      prepareScannerForNextCard();
      cardScanGuide.setMessage(getString(R.string.scan_tap_repeat));
    }
  }

  private void persistIdentifiedPrinting(CardEditionOption option, String detectedLanguage) {
    CardInfo card = new CardInfo(option.getDisplayName(), "", "", "", "1");
    card.setLanguageCode(CardLanguage.toCode(detectedLanguage));
    applyLocalScanMetadata(card, option);
    persistInfo(card);
    cardRepository.selectEdition(card.getCollectionItemId(), option, () -> kotlin.Unit.INSTANCE);
    enrichIdentifiedPrinting(card.getCollectionItemId(), option);
  }

  /** The synchronous scan stage is deliberately limited to Room-backed identity and price. */
  private void applyLocalScanMetadata(CardInfo card, CardEditionOption option) {
    card.setName(option.getDisplayName());
    card.setPrintingUuid(option.getPrintingUuid());
    card.setSetCode(option.getSetCode());
    card.setSetName(option.getSetName());
    card.setCollectorNumber(option.getCollectorNumber());
    card.setFinish(option.getFinish());
    applyEditionPrice(card, option);
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
    applyEditionPrice(card, option);
  }

  private void applyEditionPrice(CardInfo card, CardEditionOption option) {
    if (option.getPrice() != null) {
      String currency = option.getCurrency() == null ? "" : option.getCurrency();
      String display = String.format(Locale.US, "%.2f %s", option.getPrice(), currency).trim();
      card.setPrice(display);
      card.setPriceL(option.getPrice().toString());
      card.setPriceM(option.getPrice().toString());
      card.setPriceH(option.getPrice().toString());
    } else {
      card.setPrice("");
      card.setPriceL("");
      card.setPriceM("");
      card.setPriceH("");
    }
  }

  /** Completes the exact auto-selected printing in the background just as the detail screen does. */
  private void enrichIdentifiedPrinting(String collectionItemId, CardEditionOption selectedOption) {
    cardRepository.loadCard(selectedOption.getCardName(), false, false, (options, error) -> {
      if (isFinishing() || isDestroyed()) return kotlin.Unit.INSTANCE;
      if (error != null || options == null || options.isEmpty()) {
        finishScanMetadata(collectionItemId, false);
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
      persistCollectionWithoutBlockingScanner();
      rememberSessionScan(current);
      completeScanMetadataWithLanguage(current);
      return kotlin.Unit.INSTANCE;
    });
  }

  private void setUpRecyclerView() {
    mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);
    // The merged catalog and card grid have responsive/variable row heights.
    mRecyclerView.setHasFixedSize(false);
    gridMode = getPreferences(MODE_PRIVATE).getBoolean(PREF_COLLECTION_GRID, false);
    applyCollectionLayoutMode();

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
    ownedSetCatalogCheck = findViewById(R.id.checkOwnedSetCatalog);
    setCatalogStatus = findViewById(R.id.txtSetCatalogStatus);
    setCatalogProgress = findViewById(R.id.setCatalogProgress);
    setCatalogProgress.setVisibility(View.GONE);
    setCatalogAdapter = new MagicSetCatalogAdapter(this, set -> {
      startActivity(new Intent(this, SetCollectionActivity.class)
          .putExtra(SetCollectionActivity.EXTRA_SET_CODE, set.getCode())
          .putExtra(SetCollectionActivity.EXTRA_SET_NAME, set.getName()));
      return kotlin.Unit.INSTANCE;
    });
    ownedSetCatalogCheck.setChecked(getPreferences(MODE_PRIVATE)
        .getBoolean(PREF_ONLY_OWNED_SETS, false));
    ownedSetCatalogCheck.setOnCheckedChangeListener((button, checked) -> {
      getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_ONLY_OWNED_SETS, checked).apply();
      setCatalogAdapter.setOwnedSets(ownedSetCounts(), checked);
      updateSetCatalogCount();
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
    setCatalogAdapter.setOwnedSets(ownedSetCounts(), ownedSetCatalogCheck.isChecked());
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

  private void setUpPhotos() {
    photoControls = findViewById(R.id.photoControls);
    photoLibraryStatus = findViewById(R.id.txtPhotoLibraryStatus);
    photoScanAdapter = new PhotoScanLibraryAdapter(
        entry -> {
          startActivity(new Intent(this, PhotoScanDetailActivity.class)
              .putExtra(PhotoScanDetailActivity.EXTRA_PHOTO_SCAN_ID, entry.getId()));
          return kotlin.Unit.INSTANCE;
        },
        entry -> {
          new AlertDialog.Builder(this)
              .setMessage(R.string.delete_photo_scan_confirm)
              .setNegativeButton(R.string.cancel, null)
              .setPositiveButton(R.string.delete_photo_scan, (dialog, which) -> {
                activePhotoAnalyses.remove(entry.getId());
                PhotoScanStore.INSTANCE.delete(this, entry);
                showPhotos();
              })
              .show();
          return kotlin.Unit.INSTANCE;
        }
    );
    findViewById(R.id.btnChooseCardPhoto).setOnClickListener(view -> chooseCardPhoto());
  }

  private void chooseCardPhoto() {
    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    picker.addCategory(Intent.CATEGORY_OPENABLE);
    picker.setType("image/*");
    startActivityForResult(picker, RC_PICK_CARD_PHOTO);
  }

  private void showPhotos() {
    List<PhotoScanEntry> entries = PhotoScanStore.INSTANCE.entries(this);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    mRecyclerView.setAdapter(photoScanAdapter);
    photoScanAdapter.submit(entries);
    for (PhotoScanEntry entry : entries) {
      if (PhotoScanEntry.STATE_ANALYZING.equals(entry.getState())) analyzePhoto(entry);
    }
    photoLibraryStatus.setText(entries.isEmpty()
        ? getString(R.string.photo_library_empty)
        : getResources().getQuantityString(
            R.plurals.photo_library_count, entries.size(), entries.size()));
  }

  private void importAndAnalyzePhoto(Uri source) {
    photoLibraryStatus.setText(R.string.photo_importing);
    photoImportExecutor.execute(() -> {
      try {
        PhotoScanEntry entry = PhotoScanStore.INSTANCE.importPhoto(this, source);
        runOnUiThread(() -> {
          showPhotos();
          analyzePhoto(entry);
        });
      } catch (Throwable error) {
        runOnUiThread(() -> photoLibraryStatus.setText(
            error.getMessage() == null ? getString(R.string.photo_import_error) : error.getMessage()));
      }
    });
  }

  private void analyzePhoto(PhotoScanEntry entry) {
    if (!activePhotoAnalyses.add(entry.getId())) return;
    PhotoCardAnalyzer.INSTANCE.recognize(this, entry.getImagePath(), (lines, ocrError) -> {
      if (ocrError != null) {
        finishPhotoAnalysisWithError(entry, ocrError.getMessage());
        return kotlin.Unit.INSTANCE;
      }
      cardRepository.matchLocalPhotoText(lines, matches -> {
        if (matches.isEmpty()) {
          finishPhotoAnalysisWithError(entry, getString(R.string.photo_no_cards_detected));
          return kotlin.Unit.INSTANCE;
        }
        Map<String, List<PhotoCardNameMatch>> grouped = new LinkedHashMap<>();
        for (PhotoCardNameMatch match : matches) {
          String key = match.getCanonicalName().toLowerCase(Locale.ROOT);
          List<PhotoCardNameMatch> copies = grouped.get(key);
          if (copies == null) {
            copies = new ArrayList<>();
            grouped.put(key, copies);
          }
          copies.add(match);
        }
        entry.getCards().clear();
        final int[] pending = { grouped.size() };
        for (List<PhotoCardNameMatch> copies : grouped.values()) {
          PhotoCardNameMatch match = copies.get(0);
          cardRepository.loadCard(match.getCanonicalName(), false, false, (options, priceError) -> {
            CardEditionOption option = options.isEmpty()
                ? null
                : ScanPrintingPolicy.preferred(options);
            if (option == null && !options.isEmpty()) option = options.get(0);
            if (option != null) {
              entry.getCards().add(new PhotoScanCard(
                  java.util.UUID.randomUUID().toString(),
                  option.getCardName(),
                  match.getDisplayName(),
                  copies.size(),
                  option.getPrintingUuid(),
                  option.getSetCode(),
                  option.getSetName(),
                  option.getCollectorNumber(),
                  option.getFinish(),
                  option.getImageUrl() == null ? "" : option.getImageUrl(),
                  option.getTypeLine(),
                  option.getRulesText(),
                  option.getPrice(),
                  option.getCurrency() == null ? "EUR" : option.getCurrency(),
                  match.getLanguage()
              ));
            }
            pending[0]--;
            if (pending[0] == 0) {
              if (!new java.io.File(entry.getImagePath()).isFile()) {
                activePhotoAnalyses.remove(entry.getId());
                return kotlin.Unit.INSTANCE;
              }
              if (entry.getCards().isEmpty()) {
                finishPhotoAnalysisWithError(entry, getString(R.string.photo_prices_error));
              } else {
                entry.setState(PhotoScanEntry.STATE_READY);
                entry.setError("");
                PhotoScanStore.INSTANCE.save(this, entry);
                activePhotoAnalyses.remove(entry.getId());
                if (currentSection == SECTION_PHOTOS) showPhotos();
              }
            }
            return kotlin.Unit.INSTANCE;
          });
        }
        return kotlin.Unit.INSTANCE;
      });
      return kotlin.Unit.INSTANCE;
    });
  }

  private void finishPhotoAnalysisWithError(PhotoScanEntry entry, String message) {
    activePhotoAnalyses.remove(entry.getId());
    if (!new java.io.File(entry.getImagePath()).isFile()) return;
    entry.setState(PhotoScanEntry.STATE_ERROR);
    entry.setError(message == null || message.trim().length() == 0
        ? getString(R.string.photo_analysis_error) : message);
    PhotoScanStore.INSTANCE.save(this, entry);
    if (currentSection == SECTION_PHOTOS) showPhotos();
  }

  private Map<String, Integer> ownedSetCounts() {
    Map<String, Set<String>> cardKeysBySet = new LinkedHashMap<>();
    if (mBiblio == null) return new LinkedHashMap<>();
    for (CardInfo card : mBiblio.cards) {
      String code = safe(card.getSetCode()).trim();
      if (code.length() == 0) continue;
      String cardKey = safe(card.getPrintingUuid()).trim();
      if (cardKey.length() == 0) {
        cardKey = normalizeForFilter(safe(card.getName()) + "|" + safe(card.getCollectorNumber()));
      }
      String normalizedCode = code.toUpperCase(Locale.ROOT);
      Set<String> cardKeys = cardKeysBySet.get(normalizedCode);
      if (cardKeys == null) {
        cardKeys = new LinkedHashSet<>();
        cardKeysBySet.put(normalizedCode, cardKeys);
      }
      cardKeys.add(cardKey);
    }
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> entry : cardKeysBySet.entrySet()) {
      counts.put(entry.getKey(), entry.getValue().size());
    }
    return counts;
  }

  private void setUpBottomNavigation() {
    bottomNavigation = (BottomNavigationView) findViewById(R.id.bottomNavigation);
    settingsPlaceholder = findViewById(R.id.settingsPlaceholder);
    setUpPaletteSettings();
    setUpPriceSourceSettings();
    createGroupButton = findViewById(R.id.btnCreateGroup);
    createGroupButton.setOnClickListener(view -> promptForDeckCreation(null));
    if (!"0".equals(mPersistorMode)) {
      bottomNavigation.setVisibility(View.GONE);
      return;
    }
    int selectedNavigation = currentSection == SECTION_GROUPS ? R.id.nav_groups
        : currentSection == SECTION_CATALOG ? R.id.nav_catalog
        : currentSection == SECTION_PHOTOS ? R.id.nav_photos
        : currentSection == SECTION_SETTINGS ? R.id.nav_settings
        : R.id.nav_library;
    // Select before attaching the listener. Otherwise setSelectedItemId() can synchronously call
    // showSelectedSection(), and onCreate() refreshes the entire collection twice before drawing.
    bottomNavigation.setSelectedItemId(selectedNavigation);
    bottomNavigation.setOnItemSelectedListener(item -> {
      int itemId = item.getItemId();
      if (itemId == R.id.nav_library) currentSection = SECTION_LIBRARY;
      else if (itemId == R.id.nav_groups) currentSection = SECTION_GROUPS;
      else if (itemId == R.id.nav_catalog) currentSection = SECTION_CATALOG;
      else if (itemId == R.id.nav_photos) currentSection = SECTION_PHOTOS;
      else if (itemId == R.id.nav_settings) currentSection = SECTION_SETTINGS;
      currentFilterKey = "all";
      showSelectedSection();
      return true;
    });
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
    boolean photos = currentSection == SECTION_PHOTOS;
    lytRecycler.setVisibility(settings ? View.GONE : View.VISIBLE);
    settingsPlaceholder.setVisibility(settings ? View.VISIBLE : View.GONE);
    collectionControls.setVisibility(!settings && !catalog && !photos ? View.VISIBLE : View.GONE);
    setCatalogControls.setVisibility(!settings && catalog ? View.VISIBLE : View.GONE);
    photoControls.setVisibility(!settings && photos ? View.VISIBLE : View.GONE);
    totalText.setVisibility(!settings && !catalog && !photos ? View.VISIBLE : View.GONE);
    fabOcr.setVisibility(View.GONE);
    fabOcrMlKit.setVisibility(settings || catalog || photos ? View.GONE : View.VISIBLE);
    if (createGroupButton != null) {
      createGroupButton.setVisibility(!settings && currentSection == SECTION_GROUPS ? View.VISIBLE : View.GONE);
    }
    if (!settings) {
      if (catalog || photos) mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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
    double sum = 0.0;
    for (CardInfo card : lstGrp) {
      sum += PriceCurrency.amount(this, card) * card.getQuantityCount();
    }
    TextView txtTotal = (TextView) findViewById(R.id.txtTotal);
    txtTotal.setText(getString(R.string.collection_total_price,
        PriceCurrency.format(this, sum, PriceCurrency.preferred(this))));
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
    com.google.android.gms.vision.Detector<?> textRecognizer;
    if (useMlKitJapaneseOcr) {
      MlKitJapaneseTextDetector mlKitRecognizer = new MlKitJapaneseTextDetector();
      mlKitRecognizer.setProcessor(new MlKitOcrDetectorProcessor(mGraphicOverlay,
          candidates -> runOnUiThread(() -> handleAutomaticOcr(candidates))));
      textRecognizer = mlKitRecognizer;
    } else {
      TextRecognizer mobileVisionRecognizer = new TextRecognizer.Builder(context).build();
      mobileVisionRecognizer.setProcessor(new OcrDetectorProcessor(mGraphicOverlay,
          candidates -> runOnUiThread(() -> handleAutomaticOcr(candidates))));
      textRecognizer = mobileVisionRecognizer;
    }

    if (!useMlKitJapaneseOcr && !textRecognizer.isOperational()) {
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
    boolean returningToScanSession = reopenScanSessionAfterDetail;
    if (firstResume) {
      // onCreate() has just loaded and rendered this same collection. Reading the serialized file
      // and rebuilding the adapter again here delayed the first frame and compounded startup ANRs.
      firstResume = false;
    } else if ("0".equals(mPersistorMode) && mBiblio != null) {
      Biblio persisted = DataUtils.readSerializable(this, mBiblio.nameFile);
      if (persisted != null) {
        mBiblio = persisted;
        // Keep every physical scan addressable throughout an active session. Consolidating here
        // used to replace IDs after opening a detail and made session/selection/group counts drift.
        if (scannedSessionCards.isEmpty() && consolidateIdenticalCopies()) {
          DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
        }
        syncSessionCardsFromCollection();
        if (mRecyclerView != null) refreshUI();
      }
    }
    if (returningFromCardDetail) {
      cardDetailOpen = false;
      pendingDetailScrollItemId = null;
    }
    updateScanSessionUi();
    if (returningToScanSession) {
      reopenScanSessionAfterDetail = false;
      findViewById(R.id.ocrCaptureRoot).post(() -> showScanSession(false));
      return;
    }
    showNextCardReadySnackbar();
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
    flushPendingCollectionSave();
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
    sessionRefreshCoordinator.close();
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
    collectionSaveExecutor.shutdown();
    sessionPriceExecutor.shutdownNow();
    photoImportExecutor.shutdown();
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RC_PICK_CARD_PHOTO && resultCode == Activity.RESULT_OK &&
        data != null && data.getData() != null) {
      importAndAnalyzePhoto(data.getData());
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
      CardInfo current = findCollectionCard(cardinfoForUpdate.getCollectionItemId());
      if (current == null) current = cardinfoForUpdate;
      if (isSessionCard(current.getCollectionItemId())) rememberSessionScan(current);
      if (msg.what == DataProviderBase.ALL_DATA_COMPLETE) {
        completeScanMetadataWithLanguage(current);
      } else if (msg.what == DataProviderBase.ERROR) {
        finishScanMetadata(current.getCollectionItemId(), false);
      } else {
        updateCardAddedSnackbar(current, false);
      }
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
      if (isScannerReaderActive() && autoIdentifyCheck.isChecked() && !scanInProgress) {
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
      if (currentSection == SECTION_PHOTOS) {
        showPhotos();
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
    return PriceCurrency.amount(this, card);
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
      Set<String> groups = collectGroupNames(false);
      filterLabel.setText(groups.isEmpty() ? R.string.filter_cards : R.string.grouping_filter_label);
      appendGroupFilterOptions(false);
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

  private void refreshCollectionUiIfVisible() {
    if (lytRecycler != null && lytRecycler.getVisibility() == View.VISIBLE) refreshUI();
  }

  /**
   * Coalesces writes while the live scanner is visible. Serializing a large legacy collection on
   * the main thread for every local and web callback was dropping camera frames. The last state is
   * still queued after a short quiet period and immediately queued whenever the activity pauses.
   */
  private void persistCollectionWithoutBlockingScanner() {
    if (mBiblio == null || mBiblio.nameFile == null) return;
    if (!isScannerReaderActive()) {
      flushPendingCollectionSave();
      DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
      return;
    }
    if (pendingCollectionSave != null) autoOcrHandler.removeCallbacks(pendingCollectionSave);
    pendingCollectionSave = () -> {
      pendingCollectionSave = null;
      if (mBiblio != null && mBiblio.nameFile != null) {
        queueCollectionSave();
      }
    };
    // A scan burst often has 1-3 second gaps between physical cards. Waiting for a real quiet
    // period keeps legacy serialization and its allocations away from the camera/OCR hot path.
    autoOcrHandler.postDelayed(pendingCollectionSave, 8_000L);
  }

  private void flushPendingCollectionSave() {
    if (pendingCollectionSave == null) return;
    autoOcrHandler.removeCallbacks(pendingCollectionSave);
    pendingCollectionSave = null;
    if (mBiblio != null && mBiblio.nameFile != null) {
      queueCollectionSave();
    }
  }

  private void queueCollectionSave() {
    if (mBiblio == null || mBiblio.nameFile == null || collectionSaveExecutor.isShutdown()) return;
    // Snapshot on the main thread, where scanner callbacks mutate the model, then serialize only
    // the detached graph in background. This removes ArrayList.writeObject races and failed saves.
    final Biblio collection = mBiblio.snapshotForPersistence();
    final String fileName = mBiblio.nameFile;
    final Context appContext = getApplicationContext();
    collectionSaveExecutor.execute(() -> DataUtils.saveSerializable(appContext, collection, fileName));
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
        if (!activeScanGroupName.isEmpty()) cardInfo.addGroup(activeScanGroupName);
        mBiblio.addCard(cardInfo);
        // Session state is the scanner's lightweight UI. Rebuilding and prefetching the complete
        // collection here used to freeze preview frames after every recognized card.
        registerSessionScan(cardInfo);
        refreshCollectionUiIfVisible();
      }
      persistCollectionWithoutBlockingScanner();
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
        // Never fall back to a mutable list position: a session row may have been deleted while
        // its network request was in flight, and that old index can now belong to another card.
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
          current.setLanguageCode(cardinfoForUpdate.getLanguageCode());
          current.lstDescription = cardinfoForUpdate.lstDescription;
          propagateMetadataToIdenticalCopies(current);
        }
        refreshCollectionUiIfVisible();
      }
      persistCollectionWithoutBlockingScanner();
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
      openScannerWithEngine(false);
    } else if (viewId == R.id.fabOcrMlKit) {
      openScannerWithEngine(true);
    }
  }

  private void submitScannedCard(String scannedName) {
    submitScannedCard(scannedName, "");
  }

  private void setUpPriceSourceSettings() {
    Spinner currencySpinner = findViewById(R.id.displayCurrencySpinner);
    currencySpinner.setSelection(PriceCurrency.USD.equals(PriceCurrency.preferred(this)) ? 1 : 0, false);
    currencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      private boolean initialized;

      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String chosen = position == 1 ? PriceCurrency.USD : PriceCurrency.EUR;
        boolean changed = !chosen.equals(PriceCurrency.preferred(OcrCaptureActivity.this));
        PriceCurrency.select(OcrCaptureActivity.this, chosen);
        if (initialized && changed) {
          refreshUI();
          updateScanSessionUi();
        }
        initialized = true;
      }

      @Override public void onNothingSelected(AdapterView<?> parent) { }
    });
    PriceCurrency.refreshRateIfNeeded(this, updated -> {
      if (updated && !isFinishing() && !isDestroyed()) {
        refreshUI();
        updateScanSessionUi();
      }
      return kotlin.Unit.INSTANCE;
    });
    RecyclerView sources = findViewById(R.id.priceSourcesRecycler);
    sources.setLayoutManager(new LinearLayoutManager(this));
    sources.setNestedScrollingEnabled(false);
    PriceSourceAdapter adapter = new PriceSourceAdapter(
        PriceSourcePreferences.load(this), ordered -> {
          PriceSourcePreferences.save(this, ordered);
          cardRepository.invalidatePriceSourceOrder();
          return kotlin.Unit.INSTANCE;
        });
    sources.setAdapter(adapter);
    ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
      @Override public boolean onMove(@NonNull RecyclerView recyclerView,
          @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
        return adapter.move(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
      }

      @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

      @Override public boolean isLongPressDragEnabled() { return true; }
    });
    helper.attachToRecyclerView(sources);
  }

  private void submitScannedCard(String scannedName, String detectedLanguage) {
    String normalizedName = scannedName == null ? "" : scannedName.trim();
    if (normalizedName.length() == 0) {
      Snackbar.make(findViewById(R.id.ocrCaptureRoot), R.string.empty_scan_name,
          Snackbar.LENGTH_SHORT).show();
      return;
    }
    doSearch(normalizedName, detectedLanguage);
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

  private boolean isSessionCard(String collectionItemId) {
    if (collectionItemId == null) return false;
    for (CardInfo card : scannedSessionCards) {
      if (collectionItemId.equals(card.getCollectionItemId())) return true;
    }
    return false;
  }

  private int sessionCopyCount() {
    return ScanSessionCounts.total(scannedSessionCards);
  }

  private void updateScanSessionUi() {
    int sessionCopies = sessionCopyCount();
    if (scanSessionButton != null) {
      scanSessionButton.setText(getString(R.string.scan_session_count, sessionCopies));
    }
    if (scanSessionDialog != null) {
      scanSessionDialog.setTitle(getString(R.string.scan_session_title, sessionCopies));
    }
    scheduleScanSessionTotalUpdate();
    if (scanSessionOpen && scanSessionAdapter != null) scanSessionAdapter.notifyDataSetChanged();
  }

  /** Price aggregation is deliberately kept away from OCR and camera callbacks. */
  private void scheduleScanSessionTotalUpdate() {
    if (scanSessionTotalText == null) return;
    int generation = ++sessionPriceUpdateGeneration;
    if (pendingSessionPriceUpdate != null) {
      autoOcrHandler.removeCallbacks(pendingSessionPriceUpdate);
    }
    pendingSessionPriceUpdate = () -> {
      pendingSessionPriceUpdate = null;
      List<CardInfo> snapshot = new ArrayList<>(scannedSessionCards);
      if (sessionPriceExecutor.isShutdown()) return;
      sessionPriceExecutor.execute(() -> {
        double total = 0d;
        int totalCopies = 0;
        int pricedCopies = 0;
        for (CardInfo card : snapshot) {
          int quantity = card.getQuantityCount();
          totalCopies += quantity;
          Double amount = PriceCurrency.amountOrNull(getApplicationContext(), card);
          if (amount != null) {
            pricedCopies += quantity;
            total += amount * quantity;
          }
        }
        final double calculatedTotal = total;
        final int calculatedCopies = totalCopies;
        final int calculatedPricedCopies = pricedCopies;
        autoOcrHandler.post(() -> {
          if (generation != sessionPriceUpdateGeneration || isFinishing() || isDestroyed()) return;
          renderScanSessionTotal(calculatedPricedCopies, calculatedCopies, calculatedTotal);
        });
      });
    };
    // While the camera is live, coalesce metadata callbacks and let OCR frames win. The session
    // dialog stops the camera, so its total can be refreshed immediately.
    autoOcrHandler.postDelayed(pendingSessionPriceUpdate, isScannerReaderActive() ? 600L : 0L);
  }

  private void renderScanSessionTotal(int pricedCopies, int totalCopies, double total) {
    if (scanSessionTotalText == null) return;
    boolean allPricesReady = totalCopies > 0 && pricedCopies == totalCopies;
    String formattedTotal = PriceCurrency.format(this, total, PriceCurrency.preferred(this));
    scanSessionTotalText.setText(!allPricesReady && totalCopies > 0
        ? getString(R.string.scan_session_total_progress, pricedCopies, totalCopies, formattedTotal)
        : getString(R.string.scan_session_total, formattedTotal));
    int totalColor = totalCopies == 0
        ? MagicPalette.secondaryColor(this)
        : ContextCompat.getColor(this, allPricesReady
            ? R.color.scan_total_complete : R.color.scan_total_incomplete);
    scanSessionTotalText.setTextColor(totalColor);
    if (total > lastDisplayedSessionTotal + .0001d) {
      scanSessionTotalText.animate().cancel();
      scanSessionTotalText.setScaleX(.88f);
      scanSessionTotalText.setScaleY(.88f);
      scanSessionTotalText.setAlpha(.65f);
      scanSessionTotalText.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360L).start();
    }
    lastDisplayedSessionTotal = total;
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
    showScanSession(true);
  }

  /** Refreshed metadata for one printing/language updates every matching physical copy. */
  private void propagateMetadataToIdenticalCopies(CardInfo source) {
    if (mBiblio == null || source == null) return;
    String sourcePrinting = safe(source.getPrintingUuid()).trim();
    String sourceName = normalizeForFilter(source.getName());
    String sourceFinish = safe(source.getFinish()).trim();
    String sourceLanguage = CardLanguage.toCode(source.getLanguageCode());
    for (CardInfo candidate : mBiblio.cards) {
      if (candidate == source) continue;
      String candidatePrinting = safe(candidate.getPrintingUuid()).trim();
      boolean sameResolvedPrinting = !sourcePrinting.isEmpty() &&
          sourcePrinting.equals(candidatePrinting) &&
          sourceFinish.equalsIgnoreCase(safe(candidate.getFinish()).trim());
      boolean unresolvedCopy = candidatePrinting.isEmpty() &&
          sourceName.equals(normalizeForFilter(candidate.getName()));
      if ((!sameResolvedPrinting && !unresolvedCopy) ||
          !sourceLanguage.equals(CardLanguage.toCode(candidate.getLanguageCode()))) continue;
      candidate.setName(source.getName());
      candidate.setPrice(source.getBasePrice());
      candidate.setPriceL(source.getPriceL());
      candidate.setPriceM(source.getPriceM());
      candidate.setPriceH(source.getPriceH());
      candidate.setDescription(source.getDescription());
      candidate.setImgPath(source.getImgPath());
      candidate.setPrintingUuid(source.getPrintingUuid());
      candidate.setSetCode(source.getSetCode());
      candidate.setSetName(source.getSetName());
      candidate.setCollectorNumber(source.getCollectorNumber());
      candidate.setFinish(source.getFinish());
      candidate.lstDescription = source.lstDescription;
    }
    syncSessionCardsFromCollection();
  }

  private void showScanSession(boolean resetSelection) {
    syncSessionCardsFromCollection();
    updateScanSessionUi();
    if (scannedSessionCards.isEmpty()) {
      AlertDialog emptyDialog = new AlertDialog.Builder(this)
          .setTitle(getString(R.string.scan_session_title, 0))
          .setMessage(R.string.scan_session_no_cards)
          .setPositiveButton(android.R.string.ok, null)
          .create();
      showScanSessionDialog(emptyDialog);
      return;
    }
    if (resetSelection) {
      selectedSessionCardIds.clear();
      for (CardInfo card : scannedSessionCards) {
        selectedSessionCardIds.add(card.getCollectionItemId());
      }
    }
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setPadding(dp(18), dp(8), dp(10), dp(6));
    TextView hint = new TextView(this);
    hint.setText(R.string.scan_session_choose_edition_hint);
    hint.setTextColor(MagicPalette.secondaryColor(this));
    header.addView(hint, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    LinearLayout sorting = new LinearLayout(this);
    sorting.setGravity(Gravity.CENTER_VERTICAL);
    TextView sortLabel = new TextView(this);
    sortLabel.setText(R.string.sort_session_cards);
    sortLabel.setTextColor(MagicPalette.secondaryColor(this));
    sortLabel.setPadding(0, 0, dp(8), 0);
    sorting.addView(sortLabel, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    Spinner sessionSort = new Spinner(this);
    ArrayAdapter<CharSequence> sessionSortAdapter = ArrayAdapter.createFromResource(
        this, R.array.scan_session_sort_options, android.R.layout.simple_spinner_item);
    sessionSortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sessionSort.setAdapter(sessionSortAdapter);
    sessionSort.setSelection(currentSessionSortMode, false);
    sessionSort.setContentDescription(getString(R.string.sort_session_cards));
    sessionSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        currentSessionSortMode = position;
        scanSessionAdapter.setSortMode(position);
        if (scanSessionList != null) scanSessionList.setSelection(0);
      }

      @Override public void onNothingSelected(AdapterView<?> parent) { }
    });
    sorting.addView(sessionSort, new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    header.addView(sorting, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    LinearLayout refreshActions = new LinearLayout(this);
    refreshActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    Button refreshMissing = new Button(this);
    refreshMissing.setText(R.string.refresh_missing_session_cards);
    refreshMissing.setOnClickListener(view -> refreshSessionCards(true));
    refreshActions.addView(refreshMissing, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    Button refreshAll = new Button(this);
    refreshAll.setText(R.string.refresh_session_cards);
    refreshAll.setOnClickListener(view -> refreshSessionCards(false));
    refreshActions.addView(refreshAll, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    header.addView(refreshActions, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    content.addView(header, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    LinearLayout selection = new LinearLayout(this);
    selection.setGravity(Gravity.CENTER_VERTICAL);
    selection.setPadding(dp(14), 0, dp(10), dp(6));
    selectAllSessionCards = new CheckBox(this);
    selectAllSessionCards.setText(R.string.select_all_session_cards);
    selectAllSessionCards.setOnCheckedChangeListener((button, checked) -> {
      if (updatingSessionSelection) return;
      selectedSessionCardIds.clear();
      if (checked) {
        for (CardInfo card : scannedSessionCards) {
          selectedSessionCardIds.add(card.getCollectionItemId());
        }
      }
      scanSessionAdapter.notifyDataSetChanged();
      updateSessionSelectionUi();
    });
    selection.addView(selectAllSessionCards, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    sessionSelectionCount = new TextView(this);
    sessionSelectionCount.setTextColor(MagicPalette.secondaryColor(this));
    selection.addView(sessionSelectionCount, new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    createSessionGroupButton = new Button(this);
    createSessionGroupButton.setText(R.string.create_session_group);
    createSessionGroupButton.setOnClickListener(view -> promptForSessionGroup());
    selection.addView(createSessionGroupButton, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    content.addView(selection, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    ListView list = new ListView(this);
    scanSessionList = list;
    scanSessionAdapter.setSortMode(currentSessionSortMode);
    list.setAdapter(scanSessionAdapter);
    list.setMinimumHeight(dp(220));
    content.addView(list, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(getString(R.string.scan_session_title, sessionCopyCount()))
        .setView(content)
        .setPositiveButton(R.string.close, null)
        .create();
    showScanSessionDialog(dialog);
    updateSessionSelectionUi();
    if (!resetSelection) {
      list.post(() -> list.setSelectionFromTop(
          Math.max(0, pendingSessionScrollPosition), pendingSessionScrollOffset));
    }
  }

  private void updateSessionSelectionUi() {
    int selected = ScanSessionCounts.selected(scannedSessionCards, selectedSessionCardIds);
    if (sessionSelectionCount != null) {
      sessionSelectionCount.setText(getString(R.string.session_selection_count, selected));
    }
    if (createSessionGroupButton != null) createSessionGroupButton.setEnabled(selected > 0);
    if (selectAllSessionCards != null) {
      updatingSessionSelection = true;
      selectAllSessionCards.setChecked(!scannedSessionCards.isEmpty() &&
          selectedSessionCardIds.size() == scannedSessionCards.size());
      updatingSessionSelection = false;
    }
  }

  private void promptForSessionGroup() {
    if (selectedSessionCardIds.isEmpty()) {
      Toast.makeText(this, R.string.select_session_cards_first, Toast.LENGTH_SHORT).show();
      return;
    }
    final EditText input = new EditText(this);
    input.setHint(R.string.session_group_name);
    int padding = dp(20);
    input.setPadding(padding, dp(8), padding, dp(8));
    AlertDialog nameDialog = new AlertDialog.Builder(this)
        .setTitle(R.string.create_session_group)
        .setView(input)
        .setPositiveButton(R.string.create_group, null)
        .setNegativeButton(android.R.string.cancel, null)
        .create();
    nameDialog.setOnShowListener(shown -> nameDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(view -> {
          String name = input.getText().toString().trim();
          if (name.length() == 0) {
            input.setError(getString(R.string.session_group_name));
            return;
          }
          int count = 0;
          for (String collectionItemId : new ArrayList<>(selectedSessionCardIds)) {
            CardInfo card = findCollectionCard(collectionItemId);
            if (card != null) {
              card.addGroup(name);
              count += card.getQuantityCount();
            }
          }
          DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
          refreshUI();
          Toast.makeText(this, getString(R.string.group_created, name, count), Toast.LENGTH_LONG).show();
          nameDialog.dismiss();
          if (scanSessionDialog != null) scanSessionDialog.dismiss();
        }));
    nameDialog.show();
  }

  private void showScanSessionDialog(AlertDialog dialog) {
    scanSessionDialog = dialog;
    scanSessionOpen = true;
    resetScannerGateDiagnostics();
    scanStability.resetPending();
    if (mPreview != null) mPreview.stop();
    scanSessionDialog.setOnDismissListener(dismissed -> {
      scanSessionOpen = false;
      scanSessionDialog = null;
      scanSessionList = null;
      selectAllSessionCards = null;
      sessionSelectionCount = null;
      createSessionGroupButton = null;
      scanStability.resetPending();
      if (lytSearch.getVisibility() == View.VISIBLE && !cardDetailOpen) startCameraSource();
      showNextCardReadySnackbar();
    });
    scanSessionDialog.show();
  }

  private void openSessionCardDetails(CardInfo card) {
    rememberSessionScrollForDetail();
    reopenScanSessionAfterDetail = true;
    cardDetailOpen = true;
    if (scanSessionDialog != null) scanSessionDialog.dismiss();
    Intent intent = new Intent(this, Main2Activity.class);
    intent.putExtra(Main2Activity.EXTRA_CARD_NAME, card.getName());
    intent.putExtra(Main2Activity.EXTRA_COLLECTION_ITEM_ID, card.getCollectionItemId());
    startActivity(intent);
  }

  private void openSessionCardGallery(CardInfo card) {
    rememberSessionScrollForDetail();
    List<CardInfo> latestFirst = new ArrayList<>(scannedSessionCards);
    Collections.reverse(latestFirst);
    boolean opened = CardGalleryLauncher.openCards(this, latestFirst, card.getCollectionItemId());
    if (opened && scanSessionDialog != null) {
      reopenScanSessionAfterDetail = true;
      cardDetailOpen = true;
      scanSessionDialog.dismiss();
    }
  }

  private void rememberSessionScrollForDetail() {
    if (scanSessionList == null) return;
    pendingSessionScrollPosition = Math.max(0, scanSessionList.getFirstVisiblePosition());
    View first = scanSessionList.getChildAt(0);
    pendingSessionScrollOffset = first == null ? 0 : first.getTop() - scanSessionList.getPaddingTop();
  }

  private void retrySessionCard(CardInfo sessionCard, boolean forcePriceRefresh) {
    CardInfo current = findCollectionCard(sessionCard.getCollectionItemId());
    if (current == null || scanMetadataLoadingIds.contains(current.getCollectionItemId())) return;
    String id = current.getCollectionItemId();
    boolean wasComplete = isCardDataComplete(current);
    scanMetadataLoadingIds.add(id);
    scanMetadataFailedIds.remove(id);
    scanLanguageReselectAttemptedIds.remove(id);
    if (!wasComplete) scanReadyNotifiedIds.remove(id);
    updateScanSessionUi();
    int persistorIndex = findCardInfoInPersistor(current);
    if (persistorIndex >= 0) requestCardInfo(current, persistorIndex, forcePriceRefresh);
  }

  private void refreshSessionCards(boolean missingOnly) {
    ScanSessionRefreshCoordinator.Task task = new ScanSessionRefreshCoordinator.Task() {
      @Override public boolean isIncomplete(CardInfo card) { return !isCardDataComplete(card); }

      @Override public void refresh(CardInfo card, boolean forcePriceRefresh) {
        if (!scanMetadataLoadingIds.contains(card.getCollectionItemId())) {
          retrySessionCard(card, forcePriceRefresh);
        }
      }
    };
    if (missingOnly) {
      // Missing prices may not exist in yesterday's local snapshot. Refresh that snapshot once,
      // then let the coroutine dispatch only the still-incomplete rows.
      cardRepository.refreshPriceIndex(updated -> {
        sessionRefreshCoordinator.refreshMissing(scannedSessionCards, task);
        return kotlin.Unit.INSTANCE;
      });
    } else {
      cardRepository.refreshPriceIndex(updated -> {
        sessionRefreshCoordinator.refreshAll(scannedSessionCards, task);
        return kotlin.Unit.INSTANCE;
      });
    }
  }

  private void deleteSessionCard(CardInfo sessionCard) {
    deleteCardFromCollection(sessionCard);
    Toast.makeText(this, R.string.session_card_deleted, Toast.LENGTH_SHORT).show();
  }

  private void increaseSessionCardQuantity(CardInfo sessionCard) {
    CardInfo current = findCollectionCard(sessionCard.getCollectionItemId());
    if (current == null) return;
    current.setQuantityCount(current.getQuantityCount() + 1);
    persistSessionQuantityChange(current);
  }

  private void decreaseSessionCardQuantity(CardInfo sessionCard) {
    CardInfo current = findCollectionCard(sessionCard.getCollectionItemId());
    if (current == null) return;
    if (current.getQuantityCount() <= 1) {
      deleteCardFromCollection(current);
      Toast.makeText(this, R.string.last_copy_removed, Toast.LENGTH_SHORT).show();
      return;
    }
    current.setQuantityCount(current.getQuantityCount() - 1);
    persistSessionQuantityChange(current);
  }

  private void persistSessionQuantityChange(CardInfo card) {
    DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
    rememberSessionScan(card);
    refreshUI();
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

  private void playOcrRecognizedFeedback() {
    try {
      if (scanToneGenerator == null) {
        scanToneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
      }
      scanToneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 95);
    } catch (RuntimeException error) {
      Log.w(TAG, "No se pudo reproducir el sonido de OCR", error);
    }
    View root = findViewById(R.id.ocrCaptureRoot);
    root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    root.announceForAccessibility(getString(R.string.scan_name_recognized_audible));
  }

  private void registerSessionScan(CardInfo card) {
    rememberSessionScan(card);
    String cardId = card.getCollectionItemId();
    scanMetadataLoadingIds.add(cardId);
    scanMetadataFailedIds.remove(cardId);
    scanLanguageReselectAttemptedIds.remove(cardId);
    updateScanSessionUi();
  }

  private boolean isCardDataComplete(CardInfo card) {
    return card != null && ScanSessionSort.isComplete(card);
  }

  private void completeScanMetadataWithLanguage(CardInfo card) {
    if (card == null || !isSessionCard(card.getCollectionItemId())) return;
    String languageCode = CardLanguage.toCode(card.getLanguageCode());
    card.setLanguageCode(languageCode);
    if (languageCode.length() == 0 || "en".equals(languageCode)) {
      finishScanMetadata(card.getCollectionItemId(), isCardDataComplete(card));
      return;
    }
    final String collectionItemId = card.getCollectionItemId();
    if (safe(card.getSetCode()).trim().length() == 0 ||
        safe(card.getCollectorNumber()).trim().length() == 0) {
      reselectEditionForLanguage(card, languageCode);
      return;
    }
    if (!scanLanguageLoadingIds.add(collectionItemId)) return;
    scanMetadataLoadingIds.add(collectionItemId);
    cardRepository.loadImageLanguages(card.getSetCode(), card.getCollectorNumber(), (variants, error) -> {
      scanLanguageLoadingIds.remove(collectionItemId);
      CardInfo current = findCollectionCard(collectionItemId);
      if (current == null) return kotlin.Unit.INSTANCE;
      CardImageVariant localized = null;
      if (error == null && variants != null) {
        for (CardImageVariant variant : variants) {
          if (languageCode.equalsIgnoreCase(variant.getLanguageCode())) {
            localized = variant;
            break;
          }
        }
      }
      if (localized == null) {
        reselectEditionForLanguage(current, languageCode);
        return kotlin.Unit.INSTANCE;
      }
      current.setLanguageCode(localized.getLanguageCode());
      current.setImgPath(localized.getImageUrl());
      persistCollectionWithoutBlockingScanner();
      rememberSessionScan(current);
      finishScanMetadata(collectionItemId, true);
      return kotlin.Unit.INSTANCE;
    });
  }

  private void reselectEditionForLanguage(CardInfo card, String languageCode) {
    if (card == null) return;
    final String collectionItemId = card.getCollectionItemId();
    if (!scanLanguageReselectAttemptedIds.add(collectionItemId)) {
      finishScanMetadata(collectionItemId, false);
      return;
    }
    scanMetadataLoadingIds.add(collectionItemId);
    cardRepository.findLocalizedEdition(
        card.getName(), languageCode, card.getFinish(), lockedSetCodes(), (localizedOption, error) -> {
          CardInfo current = findCollectionCard(collectionItemId);
          if (current == null) return kotlin.Unit.INSTANCE;
          if (error != null || localizedOption == null) {
            finishScanMetadata(collectionItemId, false);
            return kotlin.Unit.INSTANCE;
          }
          current.setLanguageCode(languageCode);
          applyEditionMetadata(current, localizedOption);
          persistCollectionWithoutBlockingScanner();
          rememberSessionScan(current);
          cardRepository.selectEdition(collectionItemId, localizedOption, () -> {
            finishScanMetadata(collectionItemId, isCardDataComplete(current));
            return kotlin.Unit.INSTANCE;
          });
          return kotlin.Unit.INSTANCE;
        });
  }

  private void finishScanMetadata(String collectionItemId, boolean complete) {
    if (!isSessionCard(collectionItemId)) return;
    scanMetadataLoadingIds.remove(collectionItemId);
    CardInfo current = findCollectionCard(collectionItemId);
    if (current != null) rememberSessionScan(current);
    if (!complete || current == null || !isCardDataComplete(current)) {
      scanMetadataFailedIds.add(collectionItemId);
      updateScanSessionUi();
      return;
    }
    scanMetadataFailedIds.remove(collectionItemId);
    updateScanSessionUi();
    if (scanReadyNotifiedIds.add(collectionItemId)) {
      // Do not replay a long FIFO of old cards after a burst of scans. The session list keeps the
      // complete history; the transient notification should always represent the newest result.
      readyScanNotifications.clear();
      readyScanNotifications.addLast(current);
      if (activeScanSnackbar != null &&
          (activeScanCardId == null || !activeScanCardId.equals(collectionItemId))) {
        activeScanSnackbar.dismiss();
      } else {
        showNextCardReadySnackbar();
      }
    } else {
      updateCardAddedSnackbar(current, false);
    }
  }

  private void showNextCardReadySnackbar() {
    if (activeScanSnackbar != null || scanSnackbarScheduled || readyScanNotifications.isEmpty() ||
        cardDetailOpen || scanSessionOpen || isFinishing() || isDestroyed()) return;
    scanSnackbarScheduled = true;
    findViewById(R.id.ocrCaptureRoot).post(() -> {
      scanSnackbarScheduled = false;
      if (activeScanSnackbar != null || readyScanNotifications.isEmpty() ||
          cardDetailOpen || scanSessionOpen || isFinishing() || isDestroyed()) return;
      CardInfo card = readyScanNotifications.removeFirst();
      final String cardId = card.getCollectionItemId();
      activeScanCardId = cardId;
      activeScanMetadataFailed = false;
      Snackbar snackbar = Snackbar.make(
              findViewById(R.id.ocrCaptureRoot), " ", 6500)
          .setBackgroundTint(MagicPalette.primaryVariantColor(this))
          .setTextColor(Color.WHITE)
          .setActionTextColor(MagicPalette.secondaryColor(this));
      ViewGroup snackbarView = (ViewGroup) snackbar.getView();
      ViewGroup.LayoutParams rawSnackbarParams = snackbarView.getLayoutParams();
      if (rawSnackbarParams instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams snackbarParams =
            (ViewGroup.MarginLayoutParams) rawSnackbarParams;
        snackbarParams.bottomMargin += dp(40);
        snackbarView.setLayoutParams(snackbarParams);
      }
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
      TextView closeAction = snackbarAction("×", R.string.close_card_added_notification, 24f);
      closeAction.setOnClickListener(view -> snackbar.dismiss());
      summaryRow.addView(closeAction, new LinearLayout.LayoutParams(dp(44), dp(44)));
      customContent.addView(summaryRow, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      LinearLayout actionRow = new LinearLayout(this);
      actionRow.setOrientation(LinearLayout.HORIZONTAL);
      actionRow.setGravity(Gravity.CENTER_VERTICAL);

      TextView decreaseQuantity = snackbarAction("−", R.string.decrease_quantity, 24f);
      TextView quantity = new TextView(this);
      quantity.setTextColor(Color.WHITE);
      quantity.setTextSize(16f);
      quantity.setTypeface(Typeface.DEFAULT_BOLD);
      quantity.setGravity(Gravity.CENTER);
      quantity.setMinWidth(dp(36));
      quantity.setContentDescription(getString(R.string.card_quantity));
      TextView increaseQuantity = snackbarAction("+", R.string.increase_quantity, 24f);
      decreaseQuantity.setOnClickListener(view -> adjustScanSnackbarQuantity(cardId, -1));
      increaseQuantity.setOnClickListener(view -> adjustScanSnackbarQuantity(cardId, 1));
      actionRow.addView(decreaseQuantity, new LinearLayout.LayoutParams(dp(44), dp(44)));
      actionRow.addView(quantity, new LinearLayout.LayoutParams(dp(44), dp(44)));
      actionRow.addView(increaseQuantity, new LinearLayout.LayoutParams(dp(44), dp(44)));

      CheckBox foilCheck = new CheckBox(this);
      foilCheck.setText(R.string.foil);
      foilCheck.setTextColor(Color.WHITE);
      foilCheck.setTextSize(14f);
      foilCheck.setGravity(Gravity.CENTER_VERTICAL);
      foilCheck.setMinHeight(dp(44));
      foilCheck.setPadding(0, 0, dp(4), 0);
      actionRow.addView(foilCheck, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));

      View actionSpacer = new View(this);
      actionRow.addView(actionSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
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
      actionRow.addView(viewCardAction, actionParams);
      customContent.addView(actionRow, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      snackbarView.addView(customContent, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

      activeScanSnackbar = snackbar;
      activeScanThumbnail = thumbnail;
      activeScanMessage = message;
      activeScanPrice = priceBadge;
      activeScanQuantity = quantity;
      activeScanDecreaseQuantity = decreaseQuantity;
      activeScanFoil = foilCheck;
      snackbar.addCallback(new Snackbar.Callback() {
        @Override public void onDismissed(Snackbar dismissed, int event) {
          if (activeScanSnackbar != dismissed) return;
          activeScanSnackbar = null;
          activeScanThumbnail = null;
          activeScanMessage = null;
          activeScanPrice = null;
          activeScanQuantity = null;
          activeScanDecreaseQuantity = null;
          activeScanFoil = null;
          activeScanCardId = null;
          activeScanMetadataFailed = false;
          showNextCardReadySnackbar();
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
    if (activeScanThumbnail != null) {
      activeScanThumbnail.setFoilEffect(CardFinish.isFoil(card.getFinish()));
    }
    if (activeScanPrice != null) {
      String price = PriceCurrency.format(this, card);
      activeScanPrice.setText(price);
      activeScanPrice.setVisibility(price.length() == 0 ? View.GONE : View.VISIBLE);
    }
    if (activeScanQuantity != null) {
      int quantity = card.getQuantityCount();
      activeScanQuantity.setText(String.valueOf(quantity));
      activeScanQuantity.setContentDescription(
          getResources().getQuantityString(R.plurals.card_quantity_value, quantity, quantity));
    }
    if (activeScanDecreaseQuantity != null) {
      boolean canDecrease = card.getQuantityCount() > 1;
      activeScanDecreaseQuantity.setEnabled(canDecrease);
      activeScanDecreaseQuantity.setAlpha(canDecrease ? 1f : 0.35f);
    }
    if (activeScanFoil != null) {
      activeScanFoil.setOnCheckedChangeListener(null);
      activeScanFoil.setChecked(CardFinish.isFoil(card.getFinish()));
      activeScanFoil.setOnCheckedChangeListener((button, checked) ->
          adjustScannedCardFoil(card.getCollectionItemId(), checked));
    }
  }

  private TextView snackbarAction(String label, int contentDescription, float textSize) {
    TextView action = new TextView(this);
    action.setText(label);
    action.setTextColor(MagicPalette.secondaryColor(this));
    action.setTextSize(textSize);
    action.setTypeface(Typeface.DEFAULT_BOLD);
    action.setGravity(Gravity.CENTER);
    action.setMinWidth(dp(44));
    action.setMinHeight(dp(44));
    action.setContentDescription(getString(contentDescription));
    return action;
  }

  private void adjustScanSnackbarQuantity(String collectionItemId, int delta) {
    CardInfo current = findCollectionCard(collectionItemId);
    if (current == null || delta == 0) return;
    int next = Math.max(1, current.getQuantityCount() + delta);
    if (next == current.getQuantityCount()) return;
    current.setQuantityCount(next);
    persistCollectionWithoutBlockingScanner();
    rememberSessionScan(current);
    updateCardAddedSnackbar(current, false);
  }

  private void adjustScannedCardFoil(String collectionItemId, boolean foil) {
    CardInfo current = findCollectionCard(collectionItemId);
    if (current == null || CardFinish.isFoil(current.getFinish()) == foil) return;
    String finish = foil ? "foil" : "nonfoil";
    current.setFinish(finish);
    // A price belongs to one exact printing and finish. Do not leave the previous finish's value
    // visible while the already-cached edition options are resolved on the repository executor.
    current.setPrice("");
    current.setPriceL("");
    current.setPriceM("");
    current.setPriceH("");
    persistCollectionWithoutBlockingScanner();
    rememberSessionScan(current);
    updateCardAddedSnackbar(current, false);

    String printingUuid = safe(current.getPrintingUuid()).trim();
    if (printingUuid.length() == 0) return;
    cardRepository.selectPrinting(
        collectionItemId, current.getName(), printingUuid, finish, () -> kotlin.Unit.INSTANCE);
    cardRepository.loadCard(current.getName(), false, false, (options, error) -> {
      if (error != null || options == null || isFinishing() || isDestroyed()) {
        return kotlin.Unit.INSTANCE;
      }
      CardInfo latest = findCollectionCard(collectionItemId);
      if (latest == null || !finish.equalsIgnoreCase(safe(latest.getFinish()).trim())) {
        return kotlin.Unit.INSTANCE;
      }
      for (CardEditionOption option : options) {
        if (printingUuid.equals(safe(option.getPrintingUuid()).trim()) &&
            finish.equalsIgnoreCase(safe(option.getFinish()).trim())) {
          applyEditionPrice(latest, option);
          persistCollectionWithoutBlockingScanner();
          rememberSessionScan(latest);
          updateCardAddedSnackbar(latest, false);
          break;
        }
      }
      return kotlin.Unit.INSTANCE;
    });
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
      metadata.add(getString(CardFinish.isFoil(card.getFinish())
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

  /** Opens the gallery while retaining the exact row and offset of the current card list. */
  public boolean openCardGallery(CardInfo card, List<CardInfo> cards) {
    rememberCollectionScrollForDetail();
    boolean opened = CardGalleryLauncher.openCards(this, cards, card.getCollectionItemId());
    cardDetailOpen = opened;
    if (!opened) pendingDetailScrollItemId = null;
    return opened;
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
    doSearch(searchText, "");
  }

  private void doSearch(String searchText, String detectedLanguage) {
    //save and show
    CardInfo cardinfo = persistRecognizedCardName(searchText, detectedLanguage);

    requestCardInfo(cardinfo, getIdxCardInfoInPersistor(), false);
  }

  private CardInfo persistRecognizedCardName(String searchText, String detectedLanguage) {
    CardInfo card = new CardInfo(searchText, "", "", "", "");
    card.setLanguageCode(CardLanguage.toCode(detectedLanguage));
    persistInfo(card);
    return card;
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
    for (int index = scannedSessionCards.size() - 1; index >= 0; index--) {
      if (collectionItemId.equals(scannedSessionCards.get(index).getCollectionItemId())) {
        scannedSessionCards.remove(index);
      }
    }
    selectedSessionCardIds.remove(collectionItemId);
    scanMetadataLoadingIds.remove(collectionItemId);
    scanMetadataFailedIds.remove(collectionItemId);
    scanReadyNotifiedIds.remove(collectionItemId);
    scanLanguageLoadingIds.remove(collectionItemId);
    scanLanguageReselectAttemptedIds.remove(collectionItemId);
    java.util.Iterator<CardInfo> readyIterator = readyScanNotifications.iterator();
    while (readyIterator.hasNext()) {
      if (collectionItemId.equals(readyIterator.next().getCollectionItemId())) readyIterator.remove();
    }
    if (activeScanCardId != null && activeScanCardId.equals(collectionItemId) &&
        activeScanSnackbar != null) {
      activeScanSnackbar.dismiss();
    }
    DataUtils.saveSerializable(this, mBiblio, mBiblio.nameFile);
    refreshUI();
    updateScanSessionUi();
    if (scanSessionDialog != null) {
      scanSessionDialog.setTitle(getString(R.string.scan_session_title, sessionCopyCount()));
    }
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
    fabOcrMlKit.setVisibility(View.GONE);
    topLayout.setVisibility(View.VISIBLE);
    lytSearch.setVisibility(View.VISIBLE);
    lytSearch.bringToFront();
    scanStability.resetPending();
    resetScannerGateDiagnostics();
    lastOcrCharacters = "";
    lastOcrCharactersAt = 0L;
    if (scanOcrCharacters != null) {
      scanOcrCharacters.setText(R.string.scan_debug_characters_empty);
    }
    cardScanGuide.setMessage(getString(R.string.scan_align_card));
    renderNameIndexPreparation();
  }

  private void openScannerWithEngine(boolean mlKitJapanese) {
    activeScanGroupName = currentFilterKey.startsWith("group:")
        ? currentFilterKey.substring("group:".length())
        : "";
    prepareSessionForActiveGroup();
    cardRepository.prepareLockedSetOcrAliases(lockedSetCodes());
    if (useMlKitJapaneseOcr != mlKitJapanese || mCameraSource == null) {
      if (mPreview != null) mPreview.release();
      mCameraSource = null;
      useMlKitJapaneseOcr = mlKitJapanese;
      boolean autoFocus = getIntent().getBooleanExtra(App.INTENT_AUTO_FOCUS, false);
      boolean useFlash = getIntent().getBooleanExtra(App.INTENT_USE_FLASH, false);
      createCameraSource(autoFocus, useFlash);
    }
    showOcr();
    prepareNameIndexForScanner();
    startCameraSource();
  }

  /** A scanner opened from a group treats that complete group as its visible session baseline. */
  private void prepareSessionForActiveGroup() {
    if (activeScanGroupName.isEmpty()) {
      if (!sessionSourceGroupName.isEmpty()) {
        scannedSessionCards.clear();
        selectedSessionCardIds.clear();
        sessionSourceGroupName = "";
        updateScanSessionUi();
      }
      return;
    }
    scannedSessionCards.clear();
    selectedSessionCardIds.clear();
    if (mBiblio != null && mBiblio.cards != null) {
      scannedSessionCards.addAll(
          ScanSessionCounts.cardsInGroup(mBiblio.cards, activeScanGroupName));
      for (CardInfo card : scannedSessionCards) {
        selectedSessionCardIds.add(card.getCollectionItemId());
      }
    }
    sessionSourceGroupName = activeScanGroupName;
    updateScanSessionUi();
  }

  private void showRecycler() {
    hideNamePredictions();

    if (bottomNavigation != null) bottomNavigation.setVisibility(View.VISIBLE);
    showSelectedSection();
    topLayout.setVisibility(View.GONE);
    lytSearch.setVisibility(View.GONE);
    scanStability.resetPending();
    resetScannerGateDiagnostics();
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
