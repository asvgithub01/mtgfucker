package io.asv.mtgocr.ocrreader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import io.asv.mtgocr.ocrreader.model.Biblio;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/** Launcher that selects the active independent Biblio before opening the collection. */
public class MainActivity extends Activity implements View.OnClickListener {
  private Spinner libraryPicker;
  private ImageView launchBackground;
  private List<LibraryInfo> libraries = new ArrayList<>();
  private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
  private int backgroundRequest;
  private boolean updatingPicker;

  @Override protected void onCreate(Bundle savedInstanceState) {
    MagicPalette.applyTheme(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ((TextView) findViewById(R.id.txtLaunchTitle)).setTypeface(
        Typeface.createFromAsset(getAssets(), "title_font.ttf"));
    launchBackground = findViewById(R.id.imgLaunchBackground);
    libraryPicker = findViewById(R.id.spinnerLaunchLibrary);
    libraryPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (updatingPicker || position < 0 || position >= libraries.size()) return;
        LibraryCatalog.select(MainActivity.this, libraries.get(position).getId());
        refreshLaunchBackground();
      }
      @Override public void onNothingSelected(AdapterView<?> parent) { }
    });
    findViewById(R.id.btnBiblio).setOnClickListener(this);
  }

  @Override protected void onResume() {
    super.onResume();
    refreshLibraries();
  }

  private void refreshLibraries() {
    libraries = LibraryCatalog.availableLibraries(this);
    ArrayAdapter<LibraryInfo> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, libraries);
    adapter.setDropDownViewResource(R.layout.spinner_item);
    updatingPicker = true;
    libraryPicker.setAdapter(adapter);
    String activeId = LibraryCatalog.active(this).getId();
    int selected = 0;
    for (int index = 0; index < libraries.size(); index++) {
      if (activeId.equals(libraries.get(index).getId())) selected = index;
    }
    libraryPicker.setSelection(selected, false);
    libraryPicker.setVisibility(libraries.size() > 1 ? View.VISIBLE : View.GONE);
    findViewById(R.id.txtLaunchLibraryLabel).setVisibility(
        libraries.size() > 1 ? View.VISIBLE : View.GONE);
    updatingPicker = false;
    refreshLaunchBackground();
  }

  private void refreshLaunchBackground() {
    final int request = ++backgroundRequest;
    final LibraryInfo library = LibraryCatalog.active(this);
    final boolean premium = PremiumAccess.isEnabled(this);
    final String pinnedId = LibraryCatalog.pinnedBackgroundId(this, library.getId());
    backgroundExecutor.execute(() -> {
      Biblio collection = DataUtils.readSerializable(this, library.getFileName());
      List<CardInfo> cards = collection == null || collection.cards == null
          ? new ArrayList<>() : collection.cards;
      CardInfo selected = LaunchBackgroundPolicy.choose(
          cards, pinnedId, premium, size -> ThreadLocalRandom.current().nextInt(size));
      runOnUiThread(() -> {
        if (request != backgroundRequest || isFinishing() || isDestroyed()) return;
        if (selected == null) {
          CardImageCache.display(this, null, launchBackground);
          launchBackground.setImageResource(R.drawable.mtgback);
        } else {
          CardImageCache.displayKeepingCurrent(
              this, LaunchArtworkUrl.resolve(selected.getImgPath()), launchBackground);
        }
      });
    });
  }

  @Override public void onClick(View view) {
    if (view.getId() != R.id.btnBiblio) return;
    int selected = libraryPicker.getSelectedItemPosition();
    if (selected >= 0 && selected < libraries.size()) {
      LibraryCatalog.select(this, libraries.get(selected).getId());
    }
    Intent intent = new Intent(this, OcrCaptureActivity.class);
    intent.putExtra(App.INTENT_AUTO_FOCUS, ScannerSettings.autoFocus(this));
    intent.putExtra(App.INTENT_USE_FLASH, ScannerSettings.flash(this));
    intent.putExtra(App.INTENT_PERSISTOR_MODE, "0");
    startActivity(intent);
  }

  @Override protected void onDestroy() {
    backgroundRequest++;
    backgroundExecutor.shutdownNow();
    if (launchBackground != null) Glide.clear(launchBackground);
    super.onDestroy();
  }
}
