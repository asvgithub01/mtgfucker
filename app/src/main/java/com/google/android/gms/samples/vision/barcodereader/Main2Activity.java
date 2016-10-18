package com.google.android.gms.samples.vision.barcodereader;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.samples.vision.ocrreader.DataUtils;
import com.google.android.gms.samples.vision.ocrreader.R;
import com.google.android.gms.samples.vision.ocrreader.model.Biblio;
import com.google.android.gms.samples.vision.ocrreader.model.CardInfo;


public class Main2Activity extends Activity implements View.OnClickListener{
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
// To retrieve object in second Activity
        //todo pasar toooda la coleccion y e lposition
        //un gesture listener para movernos en la coleccion
        CardInfo cardInfo = (CardInfo) getIntent().getSerializableExtra("carditem");
//todo esta activity podri atener un layout diferente pal landspace y mostar a la dere las traducciones


        ImageView img  =(ImageView) findViewById(R.id.imgDetail);
        Glide
                .with(this)
                .load(cardInfo.getImgPath())
                .into(img);
        img.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
