package io.asv.mtgocr.ocrreader.data;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import io.asv.mtgocr.ocrreader.App;
import io.asv.mtgocr.ocrreader.model.CardInfo;

/**
 * Created by Alberto on 30/10/2016.
 */

public class DataProviderBase {

    //todo esta mal q se envie el all data complete en el description,
    //el all data complete deberia enviarse cuando el resto se halla enviado
    //cada provider se suscribe a dar x messages
    //WHAT_MESSAGE returns by the handler to activity
    public final static int  ERROR = 100;
    public final  static int PRICE_OK = 1; //esto podria ser el nombre del campo de cardinfo
    public final  static int IMG_OK = 2;
    public final  static int DESCRIPTION_OK = 3;
    public final  static int IMG_DESCRIPTION_OK = 4;
    public final  static int NAME_LANG_DESCRIPTION_OK = 5;
    public final  static int NAME_CARD_DESCRIPTION_OK = 6;
    public final  static int DESC_DESCRIPTION_OK = 7;
    public final  static int ALL_DATA_COMPLETE = 99;
    Handler mHandler;
    int mRequestKey = 0;
    public void sendMessage(int WHAT, CardInfo cardinfo) {
        sendMessage(WHAT, cardinfo,  -1);
    }

    public void sendMessage(int WHAT, CardInfo cardinfo, int idx) {
        Message msg = new Message();
        msg.what = WHAT;
        Bundle bundle = new Bundle();
        bundle.putInt(App.INTENT_REQUEST_KEY, this.mRequestKey); //con esta clave se busca la carta concreta, se modifica lo nuevo y se persiste
        bundle.putSerializable(App.INTENT_CARD_INFO, cardinfo);

        if (idx != -1)
            bundle.putInt(App.INTENT_IDX_DESC, idx);
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
    }

}
