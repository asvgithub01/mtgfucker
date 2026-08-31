package io.asv.mtgocr.ocrreader;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.DeckDefinition;
import java.util.List;

public final class DeckSummaryAdapter extends RecyclerView.Adapter<DeckSummaryAdapter.Holder> {
  public interface Listener {
    void onOpen(DeckDefinition deck);
    void onEdit(DeckDefinition deck);
  }

  public static final class Summary {
    public final DeckDefinition deck;
    public final CardInfo cover;
    public final int mainCount;
    public final int sideboardCount;

    public Summary(DeckDefinition deck, CardInfo cover, int mainCount, int sideboardCount) {
      this.deck = deck;
      this.cover = cover;
      this.mainCount = mainCount;
      this.sideboardCount = sideboardCount;
    }
  }

  private final List<Summary> items;
  private final boolean gridMode;
  private final Listener listener;
  private final Typeface titleTypeface;

  public DeckSummaryAdapter(List<Summary> items, boolean gridMode, Listener listener, Typeface titleTypeface) {
    this.items = items;
    this.gridMode = gridMode;
    this.listener = listener;
    this.titleTypeface = titleTypeface;
  }

  @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(LayoutInflater.from(parent.getContext()).inflate(
        gridMode ? R.layout.deck_summary_grid_item : R.layout.deck_summary_item, parent, false));
  }

  @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
    Summary item = items.get(position);
    holder.name.setTypeface(titleTypeface);
    holder.name.setText(item.deck.getName());
    holder.counts.setText(holder.itemView.getContext().getString(
        R.string.deck_card_counts, item.mainCount, item.sideboardCount));
    holder.format.setText(DeckFormatRules.byId(item.deck.getFormatId()).getLabel());
    if (item.cover != null) CardImageCache.display(holder.itemView.getContext(), item.cover.getImgPath(), holder.cover);
    else holder.cover.setImageResource(R.drawable.ic_groups);
    holder.foilBadge.setVisibility(
        item.cover != null && CardFinish.isFoil(item.cover.getFinish()) ? View.VISIBLE : View.GONE);
    holder.itemView.setOnClickListener(view -> listener.onOpen(item.deck));
    holder.edit.setOnClickListener(view -> listener.onEdit(item.deck));
    holder.itemView.animate().cancel();
    holder.itemView.setAlpha(.35f);
    holder.itemView.setScaleX(.92f);
    holder.itemView.setScaleY(.92f);
    holder.itemView.setRotationY(position % 2 == 0 ? -5f : 5f);
    holder.itemView.animate().alpha(1f).scaleX(1f).scaleY(1f).rotationY(0f).setDuration(360L).start();
  }

  @Override public int getItemCount() { return items.size(); }

  static final class Holder extends RecyclerView.ViewHolder {
    final ImageView cover;
    final ImageView foilBadge;
    final TextView name;
    final TextView counts;
    final TextView format;
    final ImageButton edit;

    Holder(View itemView) {
      super(itemView);
      cover = itemView.findViewById(R.id.imgDeckCover);
      foilBadge = itemView.findViewById(R.id.imgFoilBadge);
      name = itemView.findViewById(R.id.txtDeckName);
      counts = itemView.findViewById(R.id.txtDeckCounts);
      format = itemView.findViewById(R.id.txtDeckFormat);
      edit = itemView.findViewById(R.id.btnEditDeck);
    }
  }
}
