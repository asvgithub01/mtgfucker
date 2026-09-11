package io.asv.mtgocr.ocrreader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.CardCondition;
import io.asv.mtgocr.ocrreader.data.PriceCurrency;
import java.util.List;

/** Sortable summary of cards added during the current scanner session. */
final class ScanSessionAdapter extends BaseAdapter {
  private final Context context;
  private final List<CardInfo> cards;
  private final List<CardInfo> displayCards = new java.util.ArrayList<>();
  private final Listener listener;
  private int sortMode = ScanSessionSort.ENTRY;

  interface Listener {
    void onOpen(CardInfo card);
    void onImage(CardInfo card);
    void onCondition(CardInfo card);
    void onIncreaseQuantity(CardInfo card);
    void onDecreaseQuantity(CardInfo card);
    void onRefresh(CardInfo card);
    void onDelete(CardInfo card);
    void onSelection(CardInfo card, boolean selected);
    boolean isSelected(CardInfo card);
    boolean isLoading(CardInfo card);
  }

  ScanSessionAdapter(Context context, List<CardInfo> cards, Listener listener) {
    this.context = context;
    this.cards = cards;
    this.listener = listener;
    rebuildDisplayCards();
  }

  void setSortMode(int mode) {
    sortMode = mode;
    notifyDataSetChanged();
  }

  @Override public void notifyDataSetChanged() {
    rebuildDisplayCards();
    super.notifyDataSetChanged();
  }

  private void rebuildDisplayCards() {
    displayCards.clear();
    displayCards.addAll(ScanSessionSort.sorted(context, cards, sortMode));
  }

  @Override public int getCount() { return displayCards.size(); }

  @Override public CardInfo getItem(int position) {
    return displayCards.get(position);
  }

  @Override public long getItemId(int position) { return getItem(position).getCollectionItemId().hashCode(); }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    View view = convertView;
    if (view == null) view = LayoutInflater.from(context).inflate(R.layout.scan_session_item, parent, false);
    CardInfo card = getItem(position);
    TextView number = view.findViewById(R.id.scanSessionNumber);
    RoundedCardImageView image = view.findViewById(R.id.scanSessionImage);
    TextView name = view.findViewById(R.id.scanSessionName);
    TextView edition = view.findViewById(R.id.scanSessionEdition);
    TextView condition = view.findViewById(R.id.scanSessionCondition);
    Button decrease = view.findViewById(R.id.scanSessionDecrease);
    TextView quantity = view.findViewById(R.id.scanSessionQuantity);
    Button increase = view.findViewById(R.id.scanSessionIncrease);
    TextView price = view.findViewById(R.id.scanSessionPrice);
    ImageButton refresh = view.findViewById(R.id.scanSessionRefresh);
    ImageButton delete = view.findViewById(R.id.scanSessionDelete);
    CheckBox selected = view.findViewById(R.id.scanSessionSelected);
    name.setText(card.getName());
    int chronologicalIndex = chronologicalIndexOf(card);
    kotlin.ranges.IntRange copyRange = ScanSessionCounts.range(cards, chronologicalIndex);
    int firstCopy = copyRange.getFirst();
    int lastCopy = copyRange.getLast();
    number.setText(firstCopy == lastCopy
        ? "#" + firstCopy
        : "#" + firstCopy + "–#" + lastCopy);
    String setName = card.getSetName() == null ? "" : card.getSetName().trim();
    String setCode = card.getSetCode() == null ? "" : card.getSetCode().trim();
    String collector = card.getCollectorNumber() == null ? "" : card.getCollectorNumber().trim();
    StringBuilder details = new StringBuilder(setName);
    if (!setCode.isEmpty()) details.append(details.length() == 0 ? "" : " ").append('(').append(setCode).append(')');
    if (!collector.isEmpty()) details.append(" · #").append(collector);
    boolean loading = listener.isLoading(card);
    edition.setText(details.length() == 0
        ? context.getString(loading ? R.string.scan_metadata_loading : R.string.scan_metadata_incomplete)
        : details.toString());
    String[] conditionLabels = context.getResources().getStringArray(R.array.card_condition_labels);
    condition.setText(conditionLabels[CardCondition.indexOf(card.getCondition())]);
    condition.setOnClickListener(clicked -> listener.onCondition(card));
    quantity.setText(String.valueOf(card.getQuantityCount()));
    decrease.setOnClickListener(clicked -> listener.onDecreaseQuantity(card));
    increase.setOnClickListener(clicked -> listener.onIncreaseQuantity(card));
    String value = PriceCurrency.format(context, card);
    boolean hasPrice = !value.isEmpty();
    price.setText(hasPrice ? value : context.getString(R.string.scan_session_price_unavailable));
    price.setTextColor(ContextCompat.getColor(context,
        hasPrice ? android.R.color.white : R.color.scan_total_incomplete));
    price.setVisibility(View.VISIBLE);
    String imageUrl = card.getImgPath() == null ? "" : card.getImgPath().trim();
    image.setFoilEffect(CardFinish.isFoil(card.getFinish()));
    if (imageUrl.isEmpty()) {
      Object boundUrl = image.getTag(R.id.card_image_cache_url);
      if (!"".equals(boundUrl) || image.getDrawable() == null) {
        CardImageCache.display(context, "", image);
        image.setImageResource(R.drawable.backmtg);
      }
    } else {
      // Do not put the placeholder back before rebinding. Quantity changes notify the adapter;
      // displayKeepingCurrent intentionally keeps the bitmap when this exact URL is still bound.
      CardImageCache.displayKeepingCurrent(context, imageUrl, image);
    }
    image.setContentDescription(context.getString(R.string.open_card_gallery));
    image.setOnClickListener(clicked -> listener.onImage(card));
    refresh.setEnabled(!loading);
    refresh.setAlpha(loading ? .38f : 1f);
    refresh.setOnClickListener(clicked -> listener.onRefresh(card));
    delete.setOnClickListener(clicked -> listener.onDelete(card));
    selected.setOnCheckedChangeListener(null);
    selected.setChecked(listener.isSelected(card));
    selected.setOnCheckedChangeListener((button, checked) -> listener.onSelection(card, checked));
    view.setOnClickListener(clicked -> listener.onOpen(card));
    return view;
  }

  private int chronologicalIndexOf(CardInfo card) {
    String id = card.getCollectionItemId();
    for (int index = 0; index < cards.size(); index++) {
      CardInfo candidate = cards.get(index);
      if (candidate == card || candidate.getCollectionItemId().equals(id)) return index;
    }
    return 0;
  }
}
