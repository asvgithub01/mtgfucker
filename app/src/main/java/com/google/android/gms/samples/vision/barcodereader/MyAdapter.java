package com.google.android.gms.samples.vision.barcodereader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.samples.vision.ocrreader.OcrCaptureActivity;
import com.google.android.gms.samples.vision.ocrreader.R;
import com.google.android.gms.samples.vision.ocrreader.model.CardInfo;

import java.util.List;

/**
 * Created by Alberto on 18/10/2016.
 */
public class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder>  implements View.OnClickListener {
    private List<CardInfo> mDataset;
    private static Context mContext;
    private static int mPosition;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder  {
        // each data item is just a string in this case
        public TextView
                mtxtPrice,
                mtxtName;

        public ViewHolder(View v) {
            super(v);
            mtxtPrice = (TextView) v.findViewById(R.id.txtPrice);
            mtxtName = (TextView) v.findViewById(R.id.txtName);
            Typeface tf = Typeface.createFromAsset(mContext.getAssets(),"title_font.ttf");
            mtxtName.setTypeface(tf);
        }


    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MyAdapter(List<CardInfo> myDataset, Context context) {
        mDataset = myDataset;
        mContext = context;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_item, parent, false);
        // set the view's size, margins, paddings and layout parameters
        v.setOnClickListener(this);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }
    @Override
    public void onClick(View v) {
        Intent intent = new Intent(mContext, Main2Activity.class);
        intent.putExtra("carditem", mDataset.get(mPosition));
        mContext.startActivity(intent);

    }
    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        mPosition=position;
        holder.mtxtName.setText(mDataset.get(position).getName());
        holder.mtxtPrice.setText(mDataset.get(position).getPrice());


    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
