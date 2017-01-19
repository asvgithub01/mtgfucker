package io.asv.mtgocr.ocrreader;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import io.asv.mtgocr.ocrreader.model.CardInfo;

import java.util.ArrayList;


public class Main2Activity extends Activity implements View.OnClickListener {
    private GestureDetector gestureDetector;
    static final int SWIPE_MIN_DISTANCE = 120;
    static final int SWIPE_THRESHOLD_VELOCITY = 200;
    ArrayList<CardInfo> lstcardInfo;
    int mPosition = 0;
    ImageView mImg;
    RelativeLayout mLytDetail;
    Context mContext;
    Spinner mSpinner;
    TextView mTxtCount;
    Button mBtnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        mLytDetail = (RelativeLayout) findViewById(R.id.lytDetail);
        mTxtCount = (TextView) findViewById(R.id.txtCount);

        mImg = (ImageView) findViewById(R.id.imgDetail);
        lstcardInfo = (ArrayList<CardInfo>) getIntent().getSerializableExtra("cardList");
        mPosition = (int) getIntent().getIntExtra("cardPosition", 0);
        mContext = this;
        showImage(true);
        mImg.setLongClickable(true);
        mImg.setOnTouchListener(new View.OnTouchListener() {
            private GestureDetector gesture = new GestureDetector(mContext,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onFling(MotionEvent e1,
                                               MotionEvent e2,
                                               float velocityX,
                                               float velocityY) {
                            moveImages(e1, e2, velocityX, velocityY);
                            return true;
                        }
                    });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gesture.onTouchEvent(event);
            }
        });
        mBtnClose=(Button)findViewById(R.id.btnClose);
        mBtnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        //todo pasar toooda la coleccion y e lposition
        //un gesture listener para movernos en la coleccion
    }

    private void showImage(boolean isforward) {
        if (mPosition > lstcardInfo.size() - 1)
            mPosition = 0;

        if (mPosition < 0)
            mPosition = lstcardInfo.size() - 1;

        mImg = (ImageView) findViewById(R.id.imgDetail);
        String imgPath = "";
        imgPath = lstcardInfo.get(mPosition).getImgPath();

        if (imgPath.equals("")) {
            if (isforward)
                mPosition++;
            else
                mPosition--;
            showImage(isforward);
        } else {
            Glide
                    .with(this)
                    .load(imgPath)
                    .into(mImg);
            mTxtCount.setText(mPosition + 1 + "/" + (lstcardInfo.size()));
        }

        changeLanguagesCombo();
       /* ahora hay q meter la recycler view del docteore para poder eliminar cartas chungas
                añadir el text para ver el total del precio
                falta todo lo q tiene q ver con los decks y el manejo de estos
                y la interfaz de usu para q tenga un boton y muesrte despues la lista
*/


    }

    boolean mBFirstHit = true;

    /**
     * esto cambia el combo de traducciones
     */
    private void changeLanguagesCombo() {
        mSpinner = (Spinner) findViewById(R.id.spinner);
        mBFirstHit = true;
        String[] namesList = new String[lstcardInfo.get(mPosition).lstDescription.size()];
        for (int i = 0; i < lstcardInfo.get(mPosition).lstDescription.size() - 1; i++) {
            namesList[i] = lstcardInfo.get(mPosition).lstDescription.get(i).name;
        }

        CustomizedSpinnerAdapter adapter;
        adapter = new CustomizedSpinnerAdapter(this, R.layout.lang_item, namesList);
        adapter.setDropDownViewResource(R.layout.lang_item);
        mSpinner.setAdapter(adapter);

        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, final int position, long id) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if (!isFinishing() && !mBFirstHit) {
                            new AlertDialog.Builder(Main2Activity.this)
                                    .setTitle(lstcardInfo.get(mPosition).lstDescription.get(position).name)
                                    .setMessage(lstcardInfo.get(mPosition).lstDescription.get(position).description)
                                    .setCancelable(false)
                                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                        }
                                    }).create().show();
                        }
                        if (mBFirstHit) mBFirstHit = false;
                    }
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

    }

    @Deprecated
    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {


        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            moveImages(e1, e2, velocityX, velocityY);
            return false;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }


    }

    private boolean moveImages(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        try {
            // right to left swipe
            if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                mPosition++;
                showImage(true);
                return true;
            } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                mPosition--;
                showImage(false);
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        //finish();
    }

    public class CustomizedSpinnerAdapter extends ArrayAdapter<String> {

        private Activity context;
        String[] data = null;

        public CustomizedSpinnerAdapter(Activity context, int resource, String[] data2) {
            super(context, resource, data2);
            this.context = context;
            this.data = data2;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                //inflate your customlayout for the textview
                LayoutInflater inflater = context.getLayoutInflater();
                row = inflater.inflate(R.layout.lang_item, parent, false);
            }
            //put the data in it
            String itemDesc = data[position];

            if (itemDesc != null) {
                TextView text1 = (TextView) row.findViewById(R.id.txtNameCard);
                text1.setText(itemDesc);
                Typeface tf = Typeface.createFromAsset(mContext.getAssets(), "title_font.ttf");
                text1.setTypeface(tf);
                ImageView imgFlag = (ImageView) row.findViewById(R.id.imgFlag);
                Glide
                        .with(row.getContext())
                        .load(lstcardInfo.get(mPosition).lstDescription.get(position).imgPath)
                        .into(imgFlag);
            }

            return row;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        // This funtion called for each row ( Called data.size() times )
        public View getCustomView(int position, View convertView, ViewGroup parent) {

            /********** Inflate spinner_rows.xml file for each row ( Defined below ) ************/
            LayoutInflater inflater = context.getLayoutInflater();
            View row = inflater.inflate(R.layout.lang_item, parent, false);
/*

            tempValues = null;
            tempValues = (SpinnerModel) data.get(position);

            TextView label        = (TextView)row.findViewById(R.id.company);
            TextView sub          = (TextView)row.findViewById(R.id.sub);
            ImageView companyLogo = (ImageView)row.findViewById(R.id.image);

            if(position==0){

                // Default selected Spinner item
                label.setText("Please select company");
                sub.setText("");
            }
            else
            {
                // Set values for spinner each row
                label.setText(tempValues.getCompanyName());
                sub.setText(tempValues.getUrl());
                companyLogo.setImageResource(res.getIdentifier
                        ("com.androidexample.customspinner:drawable/"
                                + tempValues.getImage(),null,null));

            }
*/
            return row;
        }
    }

}
