package io.asv.mtgocr.ocrreader;

import android.graphics.Typeface;
import android.os.Handler;
import androidx.recyclerview.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.data.PriceCurrency;
import io.asv.mtgocr.ocrreader.model.CardCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Alberto on 18/10/2016.
 */
public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder>
    implements View.OnClickListener {

  private static final int PENDING_REMOVAL_TIMEOUT = 3000; // 3sec

  List<CardInfo> itemsPendingRemoval;
  boolean undoOn = true; // is undo on, you can turn it on from the toolbar menu

  private Handler handler = new Handler(); // hanlder for running delayed runnables
  HashMap<CardInfo, Runnable> pendingRunnables = new HashMap<>();
  // map of items to pending runnables, so we can cancel a removal if need be

  private List<CardInfo> mDataset;
  private static OcrCaptureActivity mContext;
  private final int viewMode;
  private final LibraryCaptureImages captureImages;

  // Provide a reference to the views for each data item
  // Complex data items may need more than one view per item, and
  // you provide access to all the views for a data item in a view holder
  public static class ViewHolder extends RecyclerView.ViewHolder {
    // each data item is just a string in this case
    public TextView mtxtPrice, mtxtName, mTxtUndo, mTxtGroups;
    View mLytViewHolder;
    RoundedCardImageView mImgCard;
    ImageView mFoilBadge, setSymbol;
    ImageButton btnOrganize, btnDetails, btnGallery, btnCapture;
    TextView txtQuantity;

    public ViewHolder(View v) {
      super(v);
      mtxtPrice = (TextView) v.findViewById(R.id.txtPrice);
      mtxtName = (TextView) v.findViewById(R.id.txtName);
      mTxtUndo = (TextView) v.findViewById(R.id.txtUndo);
      mTxtGroups = (TextView) v.findViewById(R.id.txtGroups);
      mImgCard = (RoundedCardImageView) v.findViewById(R.id.imgCard);
      mFoilBadge = (ImageView) v.findViewById(R.id.imgFoilBadge);
      btnOrganize = (ImageButton) v.findViewById(R.id.btnOrganizeCard);
      btnDetails = (ImageButton) v.findViewById(R.id.btnCardDetails);
      btnGallery = (ImageButton) v.findViewById(R.id.btnCardGallery);
      btnCapture = (ImageButton) v.findViewById(R.id.btnCardCapture);
      setSymbol = (ImageView) v.findViewById(R.id.imgCardSetSymbol);
      txtQuantity = (TextView) v.findViewById(R.id.txtCardQuantity);
      mLytViewHolder = v.findViewById(R.id.lytViewHolder);

      Typeface tf = Typeface.createFromAsset(mContext.getAssets(), "title_font.ttf");
      mtxtName.setTypeface(tf);
      txtQuantity.setTypeface(tf);
    }
  }

  // Provide a suitable constructor (depends on the kind of dataset)
  public MyAdapter(List<CardInfo> myDataset, OcrCaptureActivity context, int viewMode) {
    mDataset = myDataset;
    mContext = context;
    this.viewMode = CollectionViewMode.sanitize(viewMode);
    itemsPendingRemoval = new ArrayList<>();
    captureImages = this.viewMode == CollectionViewMode.CARD_GRID
        ? new LibraryCaptureImages(context, myDataset, LibraryCatalog.activeFile(context), () -> {
            if (!context.isDestroyed() && !context.isFinishing()) notifyDataSetChanged();
          }) : null;
  }

  View mLinearViewHolder;

  // Create new views (invoked by the layout manager)
  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    // create a new view
    int layout = viewMode == CollectionViewMode.CARD_GRID
        ? R.layout.card_grid_item
        : CollectionViewMode.usesArtwork(viewMode) ? R.layout.card_art_item : R.layout.card_item;
    View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
    if (v.findViewById(R.id.btnDeleteCard) == null) {
      android.widget.FrameLayout wrapper = new android.widget.FrameLayout(mContext);
      wrapper.setLayoutParams(v.getLayoutParams()); wrapper.addView(v);
      android.widget.CheckBox check = new android.widget.CheckBox(mContext);check.setId(R.id.btnDeleteCard);
      wrapper.addView(check,new android.widget.FrameLayout.LayoutParams(48,48,android.view.Gravity.TOP|android.view.Gravity.START));v=wrapper;
    }
    // set the view's size, margins, paddings and layout parameters
    //v.setOnClickListener(this);
    ViewHolder vh = new ViewHolder(v);

    mLinearViewHolder = v.findViewById(R.id.lytViewHolder);
    mLinearViewHolder.setOnTouchListener(new View.OnTouchListener() {
      private GestureDetector gesture =
          new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {

              //todo esto no se llama never, debe de mangar el evento el recycler
              //falta tb hacer bien el evento para q se trate de q haga la accion
              TranslateAnimation anim =
                  new TranslateAnimation(e1.getX(), e2.getX(), e1.getY(), e2.getY());
              anim.setDuration(1000);
              mLinearViewHolder.startAnimation(anim);
              return true;
            }
          });

      @Override public boolean onTouch(View v, MotionEvent event) {
        return gesture.onTouchEvent(event);
      }
    });
    return vh;
  }

  @Override public void onClick(View v) {

  }

  public void setUndoOn(boolean undoOn) {
    this.undoOn = undoOn;
  }

  public boolean isUndoOn() {
    return undoOn;
  }

  public void pendingRemoval(int position) {
    final CardInfo item = mDataset.get(position);
    if (!itemsPendingRemoval.contains(item)) {
      itemsPendingRemoval.add(item);
      // this will redraw row in "undo" state
      notifyItemChanged(position);
      // let's create, store and post a runnable to remove the item
      Runnable pendingRemovalRunnable = new Runnable() {
        @Override public void run() {
          int position = mDataset.indexOf(item);
          if (position >= 0) remove(position);
          mContext.deleteCardFromCollection(item);
        }
      };
      handler.postDelayed(pendingRemovalRunnable, PENDING_REMOVAL_TIMEOUT);
      pendingRunnables.put(item, pendingRemovalRunnable);
    }
  }

  public void remove(int position) {
    if (position < 0 || position >= mDataset.size()) return;
    CardInfo item = mDataset.get(position);
    if (itemsPendingRemoval != null && itemsPendingRemoval.contains(item)) {
      itemsPendingRemoval.remove(item);
    }
    if (mDataset.contains(item)) {
      mDataset.remove(position);

      notifyItemRemoved(position);
    }
  }

  public CardInfo getItem(int position) {
    return mDataset.get(position);
  }

  public boolean isPendingRemoval(int position) {
    CardInfo item = mDataset.get(position);
    return itemsPendingRemoval.contains(item);
  }

  // Replace the contents of a view (invoked by the layout manager)
  @Override public void onBindViewHolder(ViewHolder holder, int position) {
    final CardInfo deleteTarget = mDataset.get(position);
    android.widget.CheckBox selection = holder.itemView.findViewById(R.id.btnDeleteCard);
    selection.setOnCheckedChangeListener(null);
    selection.setContentDescription(deleteTarget.getName());
    selection.setChecked(mContext.isCollectionSelected(deleteTarget.getCollectionItemId()));
    selection.setOnCheckedChangeListener((button,checked)->mContext.setCollectionSelected(deleteTarget.getCollectionItemId(),checked));
    // - get element from your dataset at this position
    // - replace the contents of the view with that element
    final CardInfo item = mDataset.get(position);
    if (itemsPendingRemoval != null && itemsPendingRemoval.contains(item)) {

      holder.mLytViewHolder.setVisibility(View.GONE);
      holder.mTxtUndo.setVisibility(View.VISIBLE);
      holder.mTxtUndo.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          // user wants to undo the removal, let's cancel the pending task
          Runnable pendingRemovalRunnable = pendingRunnables.get(item);
          pendingRunnables.remove(item);
          if (pendingRemovalRunnable != null) handler.removeCallbacks(pendingRemovalRunnable);
          itemsPendingRemoval.remove(item);
          // this will rebind the row in "normal" state
          int restoredPosition = mDataset.indexOf(item);
          if (restoredPosition >= 0) notifyItemChanged(restoredPosition);
        }
      });
    } else {
      holder.mLytViewHolder.setVisibility(View.VISIBLE);
      holder.mTxtUndo.setVisibility(View.GONE);
      holder.mtxtName.setVisibility(View.VISIBLE);
      holder.mtxtPrice.setVisibility(View.VISIBLE);

      holder.mtxtName.setText(item.getName());
      holder.mtxtName.setVisibility(viewMode == CollectionViewMode.CARD_GRID ? View.GONE : View.VISIBLE);
      String priceText = PriceCurrency.format(mContext, item);
      holder.mtxtPrice.setText(priceText.isEmpty() ? mContext.getString(R.string.no_price) : PriceSourcePresentation.format(mContext, item));
      holder.mtxtPrice.setOnClickListener(v -> {
        if (mContext instanceof android.app.Activity) PriceHistoryDialog.showCard((android.app.Activity)mContext, item.getPrintingUuid(), item.getFinish());
      });
      holder.txtQuantity.setText(viewMode == CollectionViewMode.CARD_GRID
          ? mContext.getString(R.string.card_grid_quantity, item.getQuantityCount())
          : String.valueOf(item.getQuantityCount()));
      boolean foil = CardFinish.isFoil(item.getFinish());
      if (holder.mFoilBadge != null) holder.mFoilBadge.setVisibility(foil ? View.VISIBLE : View.GONE);
      holder.mImgCard.setFoilEffect(foil);
      StringBuilder groups = new StringBuilder();
      if (item.getSetName() != null && item.getSetName().trim().length() > 0) {
        if (viewMode != CollectionViewMode.LIST) {
          groups.append(mContext.getString(R.string.grid_set_overlay, item.getSetName()));
        }
        else groups.append(item.getSetName());
        if (item.getSetCode() != null && item.getSetCode().trim().length() > 0) {
          groups.append(" (").append(item.getSetCode()).append(")");
        }
      } else if (viewMode != CollectionViewMode.LIST) {
        groups.append(mContext.getString(R.string.personal_collection_label));
      }
      if (groups.length() > 0) groups.append(" · ");
      groups.append(mContext.getResources().getStringArray(R.array.card_condition_labels)[
          CardCondition.indexOf(item.getCondition())]);
      if (viewMode != CollectionViewMode.CARD_GRID && !item.getDecks().isEmpty()) {
        if (groups.length() > 0) groups.append(" · ");
        groups.append(mContext.getString(R.string.decks_prefix));
        for (int deckIndex = 0; deckIndex < item.getDecks().size(); deckIndex++) {
          if (deckIndex > 0) groups.append(", ");
          String deck = item.getDecks().get(deckIndex);
          groups.append(deck);
          if (item.isSideboardForDeck(deck)) {
            groups.append(" ").append(mContext.getString(R.string.sideboard_parenthetical));
          }
        }
      }
      if (ScanReviewStatus.hasConflict(mContext, item)) groups.insert(0, mContext.getString(R.string.scan_review_conflict) + " · ");
      if (item.isProvisionalEdition()) groups.insert(0, mContext.getString(R.string.edition_provisional) + " · ");
      holder.mTxtGroups.setText(groups.toString());
      holder.mTxtGroups.setVisibility(groups.length() > 0 ? View.VISIBLE : View.GONE);
      View.OnClickListener openEditions = new View.OnClickListener() {
        @Override public void onClick(View v) {
          mContext.openCardDetails(item);
        }
      };
      holder.mLytViewHolder.setOnClickListener(openEditions);
      holder.mtxtName.setOnClickListener(openEditions);
      if (holder.btnDetails != null) holder.btnDetails.setOnClickListener(openEditions);
      holder.mImgCard.setContentDescription(mContext.getString(R.string.open_card_gallery));
      View.OnClickListener openGallery = view -> mContext.openCardGallery(item, mDataset);
      holder.mImgCard.setOnClickListener(openGallery);
      if (holder.btnGallery != null) holder.btnGallery.setOnClickListener(openGallery);
      if (holder.setSymbol != null) {
        holder.setSymbol.setContentDescription(mContext.getString(R.string.view_card_details) + " · " + (item.getSetName() == null ? "" : item.getSetName()));
        holder.setSymbol.setOnClickListener(openEditions);
        SetSymbolLoader.INSTANCE.display(mContext, item.getSetCode() == null ? "" : item.getSetCode(), holder.setSymbol, android.R.drawable.ic_menu_info_details);
        holder.setSymbol.setColorFilter(android.graphics.Color.WHITE);
      }
      holder.btnOrganize.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          mContext.showOrganizerDialog(item);
        }
      });
      String imageUrl = CollectionViewMode.usesArtwork(viewMode)
          ? LaunchArtworkUrl.resolve(item.getImgPath())
          : item.getImgPath();
      java.io.File capture = captureImages == null ? null : captureImages.selectedPhoto(item.getCollectionItemId());
      if (holder.btnCapture != null) {
        boolean available = captureImages != null && captureImages.available(item.getCollectionItemId());
        holder.btnCapture.setEnabled(available);
        holder.btnCapture.setAlpha(available ? 1f : .3f);
        holder.btnCapture.setContentDescription(mContext.getString(!available ? R.string.card_capture_unavailable
            : capture == null ? R.string.card_capture_show : R.string.card_capture_scryfall));
        holder.btnCapture.setImageResource(capture == null ? android.R.drawable.ic_menu_camera : android.R.drawable.ic_menu_gallery);
        holder.btnCapture.setOnClickListener(v -> {
          captureImages.toggle(item.getCollectionItemId());
          int current = indexOfCollectionItem(item.getCollectionItemId());
          if (current >= 0) notifyItemChanged(current);
        });
      }
      if (capture != null) {
        CardImageCache.showLoading(holder.mImgCard);
        com.bumptech.glide.Glide.with(mContext).load(capture).dontAnimate()
            .placeholder(new CardLoadingDrawable()).error(R.drawable.backmtg).into(holder.mImgCard);
        holder.mImgCard.setFoilEffect(false);
      } else {
        CardImageCache.display(mContext, imageUrl == null ? item.getImgPath() : imageUrl, holder.mImgCard);
      }
      if (viewMode != CollectionViewMode.LIST) {
        holder.itemView.animate().cancel();
        // Never fade a recycled image from near-transparent: Glide may finish in the same frame,
        // which looked like a brightness flash while scrolling the gallery. Keep only a restrained
        // spatial entrance so cards still feel alive without blinking.
        holder.itemView.setAlpha(1f);
        holder.itemView.setScaleX(.97f);
        holder.itemView.setScaleY(.97f);
        holder.itemView.setRotationY(position % 2 == 0 ? -2f : 2f);
        holder.itemView.animate().scaleX(1f).scaleY(1f).rotationY(0f)
            .setDuration(220L).start();
      }
    }
  }

  // Return the size of your dataset (invoked by the layout manager)
  @Override public int getItemCount() {
    return mDataset.size();
  }

  public int indexOfCollectionItem(String collectionItemId) {
    if (collectionItemId == null) return -1;
    for (int index = 0; index < mDataset.size(); index++) {
      if (collectionItemId.equals(mDataset.get(index).getCollectionItemId())) return index;
    }
    return -1;
  }
}
