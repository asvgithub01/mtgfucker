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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
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
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import io.asv.mtgocr.ocrreader.data.DataProviderBase;
import io.asv.mtgocr.ocrreader.data.CardRepository;
import io.asv.mtgocr.ocrreader.data.CardNameSuggestion;
import io.asv.mtgocr.ocrreader.data.IDataProvider;
import io.asv.mtgocr.ocrreader.data.MtgJsonRoomDataProvider;
import io.asv.mtgocr.ocrreader.model.Biblio;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.Deck;
import io.asv.mtgocr.ocrreader.model.Decks;
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
  FloatingActionButton fabOcr;
  EditText txtSearch;
  RelativeLayout lytSearch;
  LinearLayout lytRecycler, topLayout;
  private Spinner sortSpinner, filterSpinner;
  private TextView filterLabel;
  private View collectionControls;
  private TextView totalText;
  private ImageButton viewModeButton;
  private BottomNavigationView bottomNavigation;
  private View settingsPlaceholder;
  private View createGroupButton;
  private EditText collectionSearch;
  private int currentSortMode = 0;
  private String currentFilterKey = "all";
  private String currentTextFilter = "";
  private boolean updatingFilterSpinner = false;
  private int expansionBackgroundRequest = 0;
  private final Random expansionBackgroundRandom = new Random();
  private final List<GroupFilterOption> filterOptions = new ArrayList<>();
  private static final int SECTION_LIBRARY = 0;
  private static final int SECTION_SETS = 1;
  private static final int SECTION_GROUPS = 2;
  private static final int SECTION_SETTINGS = 3;
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

  /**
   * Initializes the UI and creates the detector pipeline.
   */
  @Override public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.ocr_capture);
    cardRepository = CardRepository.get(this);
    //region asv

    imgBgCard = (ImageView) findViewById(R.id.imgBgCard);
    btnOk = (Button) findViewById(R.id.btnOk);
    btnCancel = (Button) findViewById(R.id.btnCancel);
    fabOcr = (FloatingActionButton) findViewById(R.id.fabOcr);
    txtSearch = (EditText) findViewById(R.id.txtSearch);
    cardNameSuggestions = (ListView) findViewById(R.id.cardNameSuggestions);
    lytSearch = (RelativeLayout) findViewById(R.id.lytSearch);

    lytRecycler = (LinearLayout) findViewById(R.id.lytRecycler);
    topLayout = (LinearLayout) findViewById(R.id.topLayout);
    btnOk.setOnClickListener(this);
    btnCancel.setOnClickListener(this);
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
    setUpBottomNavigation();
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
      doSearch(selectedName);
      showRecycler();
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

  private void setUpBottomNavigation() {
    bottomNavigation = (BottomNavigationView) findViewById(R.id.bottomNavigation);
    settingsPlaceholder = findViewById(R.id.settingsPlaceholder);
    createGroupButton = findViewById(R.id.btnCreateGroup);
    createGroupButton.setOnClickListener(view -> promptForBulkGroup());
    if (!"0".equals(mPersistorMode)) {
      bottomNavigation.setVisibility(View.GONE);
      return;
    }
    bottomNavigation.setOnItemSelectedListener(item -> {
      int itemId = item.getItemId();
      if (itemId == R.id.nav_library) currentSection = SECTION_LIBRARY;
      else if (itemId == R.id.nav_sets) currentSection = SECTION_SETS;
      else if (itemId == R.id.nav_groups) currentSection = SECTION_GROUPS;
      else if (itemId == R.id.nav_settings) currentSection = SECTION_SETTINGS;
      if (currentSection == SECTION_SETS) {
        currentFilterKey = getPreferences(MODE_PRIVATE).getString(PREF_LAST_SET_FILTER, "");
      } else {
        currentFilterKey = "all";
      }
      showSelectedSection();
      return true;
    });
    bottomNavigation.setSelectedItemId(R.id.nav_library);
  }

  private void showSelectedSection() {
    boolean settings = currentSection == SECTION_SETTINGS;
    lytRecycler.setVisibility(settings ? View.GONE : View.VISIBLE);
    settingsPlaceholder.setVisibility(settings ? View.VISIBLE : View.GONE);
    fabOcr.setVisibility(settings ? View.GONE : View.VISIBLE);
    if (createGroupButton != null) {
      createGroupButton.setVisibility(!settings && currentSection == SECTION_GROUPS ? View.VISIBLE : View.GONE);
    }
      if (!settings) refreshUI();
    if (!settings) updateSectionBackground();
  }

  private void updateSectionBackground() {
    boolean expansions = currentSection == SECTION_SETS;
    expansionBackgroundRequest++;
    imgBgCard.setVisibility(expansions ? View.VISIBLE : View.GONE);
    mRecyclerView.setBackgroundColor(expansions ? Color.TRANSPARENT : getColorCompat(R.color.mtg_parchment));
    lytRecycler.setBackgroundColor(Color.TRANSPARENT);
    if (collectionControls != null) {
      collectionControls.setBackgroundResource(expansions ? R.drawable.bg_glass_controls : R.color.mtg_parchment);
      if (expansions) {
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        collectionControls.setPadding(margin, margin, margin, margin);
      }
    }
    if (totalText != null) {
      totalText.setBackgroundResource(expansions ? R.drawable.bg_glass_controls : android.R.color.white);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      imgBgCard.setRenderEffect(expansions
          ? RenderEffect.createBlurEffect(9f, 9f, Shader.TileMode.CLAMP)
          : null);
    }
    if (expansions) showRandomExpansionBackground();
  }

  private int getColorCompat(int colorResource) {
    return ContextCompat.getColor(this, colorResource);
  }

  private void showRandomExpansionBackground() {
    if (mBiblio == null || currentSection != SECTION_SETS) return;
    final int request = ++expansionBackgroundRequest;
    List<CardInfo> candidates = new ArrayList<>();
    for (CardInfo card : mBiblio.cards) {
      if (("all".equals(currentFilterKey) || matchesCurrentFilter(card)) &&
          safe(card.getImgPath()).trim().length() > 0) {
        candidates.add(card);
      }
    }
    if (!candidates.isEmpty()) {
      CardInfo chosen = candidates.get(expansionBackgroundRandom.nextInt(candidates.size()));
      displayExpansionBackground(chosen.getImgPath());
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
      if (request != expansionBackgroundRequest || currentSection != SECTION_SETS || error != null) {
        return kotlin.Unit.INSTANCE;
      }
      List<String> remoteImages = new ArrayList<>();
      for (io.asv.mtgocr.ocrreader.data.SetCardOption option : cards) {
        if (option.getImageUrl() != null && option.getImageUrl().trim().length() > 0) {
          remoteImages.add(option.getImageUrl());
        }
      }
      if (!remoteImages.isEmpty()) {
        displayExpansionBackground(remoteImages.get(expansionBackgroundRandom.nextInt(remoteImages.size())));
      }
      return kotlin.Unit.INSTANCE;
    });
  }

  private void displayExpansionBackground(String imageUrl) {
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
          mLstCardInfo.set(idxOfGetterCardInfo, cInfFromDataProvider);
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
      updateFilterOptions();
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
            return bySet != 0 ? bySet : safe(left.getName()).compareToIgnoreCase(safe(right.getName()));
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
          + "|" + safe(card.getFinish()).trim().toLowerCase(Locale.ROOT);
      CardInfo representative = representatives.get(key);
      if (representative == null) {
        representatives.put(key, card);
        merged.add(card);
      } else {
        representative.setQuantityCount(representative.getQuantityCount() + card.getQuantityCount());
        for (String group : card.getGroups()) representative.addGroup(group);
        for (String deck : card.getDecks()) representative.addDeck(deck);
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
    if (raw == null || raw.trim().length() == 0) raw = card.getPrice();
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
    filterOptions.add(new GroupFilterOption("all", getString(R.string.all_cards)));
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
      filterLabel.setText(R.string.grouping_filter_label);
      appendGroupFilterOptions(false);
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

  private void promptForBulkGroup() {
    final EditText input = new EditText(this);
    input.setHint(R.string.group_name);
    int padding = (int) (20 * getResources().getDisplayMetrics().density);
    input.setPadding(padding, 0, padding, 0);
    new AlertDialog.Builder(this)
        .setTitle(R.string.create_group)
        .setMessage(collectGroupNames(false).isEmpty() ? getString(R.string.create_first_group) : "")
        .setView(input)
        .setPositiveButton(R.string.ok, (dialog, which) -> {
          String name = input.getText().toString().trim();
          if (name.length() == 0) return;
          Intent intent = new Intent(this, GroupBuilderActivity.class);
          intent.putExtra(GroupBuilderActivity.EXTRA_GROUP_NAME, name);
          intent.putExtra(GroupBuilderActivity.EXTRA_SORT, currentSortMode);
          intent.putExtra(GroupBuilderActivity.EXTRA_QUERY, currentTextFilter);
          startActivity(intent);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void appendGroupFilterOptions(boolean decks) {
    for (String name : collectGroupNames(decks)) {
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
        getString(R.string.add_to_group),
        getString(R.string.add_to_deck),
        getString(R.string.remove_assignment)
    };
    new AlertDialog.Builder(this)
        .setTitle(R.string.organize_card)
        .setItems(options, (dialog, which) -> {
          if (which == 0) showGroupPicker(card, false);
          if (which == 1) showGroupPicker(card, true);
          if (which == 2) showRemoveAssignmentDialog(card);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showGroupPicker(final CardInfo card, final boolean deck) {
    List<String> existing = new ArrayList<>(collectGroupNames(deck));
    existing.removeAll(deck ? card.getDecks() : card.getGroups());
    existing.add(getString(deck ? R.string.new_deck : R.string.new_group));
    final String[] names = existing.toArray(new String[0]);
    new AlertDialog.Builder(this)
        .setTitle(deck ? R.string.add_to_deck : R.string.add_to_group)
        .setItems(names, (dialog, which) -> {
          if (which == names.length - 1) {
            showNewGroupDialog(card, deck);
          } else {
            addCardToGroup(card, names[which], deck);
          }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void showNewGroupDialog(final CardInfo card, final boolean deck) {
    final EditText input = new EditText(this);
    input.setHint(deck ? R.string.deck_name : R.string.group_name);
    int padding = (int) (20 * getResources().getDisplayMetrics().density);
    input.setPadding(padding, 0, padding, 0);
    new AlertDialog.Builder(this)
        .setTitle(deck ? R.string.new_deck : R.string.new_group)
        .setView(input)
        .setPositiveButton(R.string.ok, (dialog, which) -> addCardToGroup(card, input.getText().toString(), deck))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void addCardToGroup(CardInfo card, String name, boolean deck) {
    boolean changed = deck ? card.addDeck(name) : card.addGroup(name);
    if (!changed) return;
    saveCollectionAndRefresh();
    Toast.makeText(this, R.string.card_assignment_saved, Toast.LENGTH_SHORT).show();
  }

  private void showRemoveAssignmentDialog(final CardInfo card) {
    final List<AssignmentRef> assignments = new ArrayList<>();
    for (String name : card.getGroups()) {
      assignments.add(new AssignmentRef(name, false, getString(R.string.group_filter, name)));
    }
    for (String name : card.getDecks()) {
      assignments.add(new AssignmentRef(name, true, getString(R.string.deck_filter, name)));
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
          if (assignment.deck) card.removeDeck(assignment.name);
          else card.removeGroup(assignment.name);
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
          current.setPrice(cardinfoForUpdate.getPrice());
          current.setPriceL(cardinfoForUpdate.getPriceL());
          current.setPriceM(cardinfoForUpdate.getPriceM());
          current.setPriceH(cardinfoForUpdate.getPriceH());
          current.setDescription(cardinfoForUpdate.getDescription());
          current.setImgPath(cardinfoForUpdate.getImgPath());
          current.setPrintingUuid(cardinfoForUpdate.getPrintingUuid());
          current.setSetCode(cardinfoForUpdate.getSetCode());
          current.setSetName(cardinfoForUpdate.getSetName());
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
      doSearch(txtSearch.getText().toString());
      showRecycler();
    } else if (viewId == R.id.btnCancel) {
      showRecycler();
    } else if (viewId == R.id.fabOcr) {
      showOcr();
    }
  }

  private void showCardAddedSnackbar(CardInfo card) {
    lytRecycler.post(() -> Snackbar.make(
            lytRecycler, getString(R.string.card_added, card.getName()), Snackbar.LENGTH_LONG)
        .setAction(R.string.view_card, view -> {
          openCardDetails(card);
        })
        .show());
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
  }

  private void showRecycler() {
    hideNamePredictions();

    if (bottomNavigation != null) bottomNavigation.setVisibility(View.VISIBLE);
    showSelectedSection();
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
