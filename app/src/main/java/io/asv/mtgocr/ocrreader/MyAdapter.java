package io.asv.mtgocr.ocrreader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import io.asv.mtgocr.ocrreader.model.CardInfo;
import java.io.Serializable;
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

  private  List<CardInfo> mDataset;
  private static Context mContext;
  private static int mPosition;

  // Provide a reference to the views for each data item
  // Complex data items may need more than one view per item, and
  // you provide access to all the views for a data item in a view holder
  public static class ViewHolder extends RecyclerView.ViewHolder {
    // each data item is just a string in this case
    public TextView mtxtPrice, mtxtName, mTxtUndo;
    LinearLayout mLytViewHolder;
    ImageView mImgCard;

    public ViewHolder(View v) {
      super(v);
      mtxtPrice = (TextView) v.findViewById(R.id.txtPrice);
      mtxtName = (TextView) v.findViewById(R.id.txtName);
      mTxtUndo = (TextView) v.findViewById(R.id.txtUndo);
      mImgCard = (ImageView) v.findViewById(R.id.imgCard);

      mLytViewHolder = (LinearLayout) v.findViewById(R.id.lytViewHolder);

      Typeface tf = Typeface.createFromAsset(mContext.getAssets(), "title_font.ttf");
      mtxtName.setTypeface(tf);
    }
  }

  // Provide a suitable constructor (depends on the kind of dataset)
  public MyAdapter(List<CardInfo> myDataset, Context context) {
    mDataset = myDataset;
    mContext = context;
    itemsPendingRemoval = new ArrayList<>();
  }

  LinearLayout mLinearViewHolder;

  // Create new views (invoked by the layout manager)
  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    // create a new view
    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_item, parent, false);
    // set the view's size, margins, paddings and layout parameters
    //v.setOnClickListener(this);
    ViewHolder vh = new ViewHolder(v);

    mLinearViewHolder = (LinearLayout) v.findViewById(R.id.lytViewHolder);
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
          remove(mDataset.indexOf(item));
          //for keep changes
          OcrCaptureActivity.mBiblio.cards.clear();
          OcrCaptureActivity.mBiblio.cards.addAll(mDataset);
        }
      };
      handler.postDelayed(pendingRemovalRunnable, PENDING_REMOVAL_TIMEOUT);
      pendingRunnables.put(item, pendingRemovalRunnable);
    }
  }

  public void remove(int position) {
    CardInfo item = mDataset.get(position);
    if (itemsPendingRemoval != null && itemsPendingRemoval.contains(item)) {
      itemsPendingRemoval.remove(item);
    }
    if (mDataset.contains(item)) {
      mDataset.remove(position);

      notifyItemRemoved(position);
    }
  }

  public boolean isPendingRemoval(int position) {
    CardInfo item = mDataset.get(position);
    return itemsPendingRemoval.contains(item);
  }

  // Replace the contents of a view (invoked by the layout manager)
  @Override public void onBindViewHolder(ViewHolder holder, int position) {
    // - get element from your dataset at this position
    // - replace the contents of the view with that element
    final CardInfo item = mDataset.get(position);
    mPosition = position;
    if (itemsPendingRemoval != null && itemsPendingRemoval.contains(item)) {

      holder.mLytViewHolder.setVisibility(View.GONE);
      holder.mTxtUndo.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          // user wants to undo the removal, let's cancel the pending task
          Runnable pendingRemovalRunnable = pendingRunnables.get(item);
          pendingRunnables.remove(item);
          if (pendingRemovalRunnable != null) handler.removeCallbacks(pendingRemovalRunnable);
          itemsPendingRemoval.remove(item);
          // this will rebind the row in "normal" state
          notifyItemChanged(mPosition);
        }
      });
    } else {
      holder.mLytViewHolder.setVisibility(View.VISIBLE);
      holder.mtxtName.setVisibility(View.VISIBLE);
      holder.mtxtPrice.setVisibility(View.VISIBLE);

      holder.mtxtName.setText(item.getName());
      holder.mtxtPrice.setText(item.getPrice());
      holder.mtxtName.setTag(position);
      holder.mtxtName.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          Intent intent = new Intent(v.getContext(), Main2Activity.class);
          intent.putExtra("cardList", (Serializable) mDataset);
          intent.putExtra("cardPosition", (int) v.getTag()); //
          v.getContext().startActivity(intent);
        }
      });
      Glide
          .with(mContext)
          .load(item.getImgPath())
          .into(holder.mImgCard);

    }
  }

  // Return the size of your dataset (invoked by the layout manager)
  @Override public int getItemCount() {
    return mDataset.size();
  }
}
