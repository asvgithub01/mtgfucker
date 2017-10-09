package io.asv.mtgocr.ocrreader.data;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import io.asv.mtgocr.ocrreader.model.CardInfo;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;


/**
 * Created by Alberto on 25/10/2016.
 */

public class MkmDataProvider  extends DataProviderBase implements IDataProvider {

    String UrlBase = "https://es.magiccardmarket.eu/Cards/";
    Context mContext;
    public MkmDataProvider(Context mContext, Handler handler, int requestKey) {
        this.mContext = mContext;
        this.mHandler = handler;
        this.mRequestKey = requestKey;
    }
    @Override
    public void GetCardInfo(String name_card, final CardInfo cardInfo) {
        String textForurl = name_card.replace(" ", "+").replace("(", "").replace("|", "");
        getHtmlInfoFromMkm(UrlBase + textForurl, cardInfo);
    }
    private void getHtmlInfoFromMkm(String url, final CardInfo cardInfo) {
        try {
            //txtDeckResult.setText(txtDeckResult.getText() + "\n" + texti);
            //txtResults.setText(txtResults.getText() + "\n" + texti);

            Ion.with(this.mContext.getApplicationContext()).load(url).asString().setCallback(
                    new FutureCallback<String>() {
                        @Override
                        public void onCompleted(Exception e, String a) {
                            try {
                                getPriceFromMkm(a, cardInfo);
                                getImgCardFromMkm(a, cardInfo);
                                getTranslateDescriptionFromMkm(a, cardInfo);
                                //->callbackdescriptin persistInfo(cardInfo);
                            } catch (Exception ex) {
                                sendMessage(ERROR, cardInfo);
                                Log.e("Error,parsing", ex.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {

            Log.e("Error,HtmlInfo", e.getMessage());
        }
    }
    private void getTranslateDescriptionFromMkm(String a, final CardInfo cardinfo) throws Exception {
        //link al otro idioma
        int ini = a.indexOf("onmouseout=\"hideMsgBox()\"></span><a href=\"");
        int fin = a.indexOf("\" class=\"nameLink\">", ini);
        String urlLang = "https://es.magiccardmarket.eu" + a.substring(ini + "onmouseout=\"hideMsgBox()\"></span><a href=\"".length(), fin);

        Ion.with(this.mContext.getApplicationContext()).load(urlLang).asString().setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String a) {

                try {
                    //get
                    int ini = a.indexOf("<div class=\"infoBlock rulesBlock");
                    ini = a.indexOf("itemprop=\"description\">", ini);
                    int fin = a.indexOf("</div>", ini);
                    String descLang = a.substring(ini + "itemprop=\"description\">".length(), fin);
                    // txtDescription.setText(urlLang.replace("<br>", "").replace("<br/>", ""));
                    //set
                    cardinfo.setDescription(descLang);
                    //send
                    sendMessage(DESCRIPTION_OK, cardinfo);
                    //persist Info
                    sendMessage(ALL_DATA_COMPLETE,cardinfo);
                } catch (Exception e1) {
                    e1.printStackTrace();
                    sendMessage(ERROR, cardinfo);
                }
            }
        });
    }
    private void getImgCardFromMkm(String a, final CardInfo cardinfo) throws Exception {
        try {
            //get
            int ini = a.indexOf("id=\"imgDiv\">");
            ini = a.indexOf("<img src=\"", ini);
            int fin = a.indexOf("\" alt=", ini);
            String imgPath = "https://es.magiccardmarket.eu/" + a.substring(ini + "<img src=\"".length(), fin);
            //set
            cardinfo.setImgPath(imgPath);
            //send
            sendMessage(IMG_OK, cardinfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void getPriceFromMkm(String a, final CardInfo cardinfo) throws Exception {
        //get
        int ini = a.indexOf("\"lowPrice\">");
        int fin = a.indexOf("</span>", ini);
        String price = a.substring(ini + "\"lowPrice\">".length(), fin);
        if (price.length() > 5)
            price = "caca futi";
        else
            cardinfo.setPrice(price);
        //set
        cardinfo.setPrice(price);
        //Intent DataUtils = new Intent();
        //send
        sendMessage(PRICE_OK, cardinfo);
    }
}
