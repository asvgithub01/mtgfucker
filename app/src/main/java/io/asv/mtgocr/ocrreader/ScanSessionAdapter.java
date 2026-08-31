package io.asv.mtgocr.ocrreader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.CardCondition;
import java.util.List;

/** Latest-first summary of cards added during the current scanner session. */
final class ScanSessionAdapter extends BaseAdapter {
  private final Context context;
  private final List<CardInfo> cards;
  private final OnConditionClick onConditionClick;

  interface OnConditionClick { void onClick(CardInfo card); }

  ScanSessionAdapter(Context context, List<CardInfo> cards, OnConditionClick onConditionClick) {
    this.context = context;
    this.cards = cards;
    this.onConditionClick = onConditionClick;
  }

  @Override public int getCount() { return cards.size(); }

  @Override public CardInfo getItem(int position) {
    return cards.get(cards.size() - 1 - position);
  }

  @Override public long getItemId(int position) { return getItem(position).getCollectionItemId().hashCode(); }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    View view = convertView;
    if (view == null) view = LayoutInflater.from(context).inflate(R.layout.scan_session_item, parent, false);
    CardInfo card = getItem(position);
    ImageView image = view.findViewById(R.id.scanSessionImage);
    TextView name = view.findViewById(R.id.scanSessionName);
    TextView edition = view.findViewById(R.id.scanSessionEdition);
    TextView condition = view.findViewById(R.id.scanSessionCondition);
    TextView price = view.findViewById(R.id.scanSessionPrice);
    name.setText(card.getName());
    String setName = card.getSetName() == null ? "" : card.getSetName().trim();
    String setCode = card.getSetCode() == null ? "" : card.getSetCode().trim();
    String collector = card.getCollectorNumber() == null ? "" : card.getCollectorNumber().trim();
    StringBuilder details = new StringBuilder(setName);
    if (!setCode.isEmpty()) details.append(details.length() == 0 ? "" : " ").append('(').append(setCode).append(')');
    if (!collector.isEmpty()) details.append(" · #").append(collector);
    edition.setText(details.length() == 0 ? context.getString(R.string.scan_metadata_loading) : details.toString());
    String[] conditionLabels = context.getResources().getStringArray(R.array.card_condition_labels);
    condition.setText(conditionLabels[CardCondition.indexOf(card.getCondition())]);
    condition.setOnClickListener(clicked -> onConditionClick.onClick(card));
    String value = card.getPrice() == null ? "" : card.getPrice().trim();
    price.setText(value);
    price.setVisibility(value.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    image.setImageResource(R.drawable.backmtg);
    if (card.getImgPath() != null && !card.getImgPath().trim().isEmpty()) {
      CardImageCache.displayKeepingCurrent(context, card.getImgPath(), image);
    }
    return view;
  }
}
