package io.asv.mtgocr.ocrreader.data;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import io.asv.mtgocr.ocrreader.model.CardInfo;
import io.asv.mtgocr.ocrreader.model.DescriptionMtgInfo;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.util.ArrayList;

/**
 * Created by Alberto on 25/10/2016.
 */

public class MtgDataProvider extends DataProviderBase implements IDataProvider {
    String UrlQueryBase = "http://magiccards.info/query?q=";
    Context mContext;

    int mIdx = -1;

    public MtgDataProvider(Context mContext, Handler handler, int requestKey) {
        this.mContext = mContext;
        this.mHandler = handler;
        this.mRequestKey = requestKey;
    }

    @Override
    public void GetCardInfo(String name_card, final CardInfo cardInfo) {
        String textForurl = name_card.replace(" ", "+").replace("(", "").replace("|", "");
        getHtmlInfoFromMtginfo(UrlQueryBase + textForurl, cardInfo);
    }

    private void getHtmlInfoFromMtginfo(String url, final CardInfo cardInfo) {
        try {
            Ion.with(this.mContext.getApplicationContext()).load(url).asString()
                    .setCallback(new FutureCallback<String>() {
                        @Override
                        public void onCompleted(Exception e, String a) {
                            try {
                                getPriceFromMtgInfo(a, cardInfo);
                                getImgCardFromMtgInfo(a, cardInfo);
                                getTranslateDescriptionsFromMtgInfo(a, cardInfo);
                                int paradaTontaPaVerqVaEnBiblio = 0;

                            } catch (Exception ex) {
                                Log.e("Error,parsing", ex.getMessage());
                                sendMessage(ERROR, cardInfo);
                            }
                        }
                    });
        } catch (Exception e) {

            Log.e("Error,HtmlInfo", e.getMessage());
        }
    }

    private void getTranslateDescriptionsFromMtgInfo(String a, final CardInfo cardinfo) {
        try {

            ArrayList<String> lstUrlLang = new ArrayList<>();
            //region parseo Description
            //region Description 1
            //region get img banderita
            int ini = a.indexOf("Languages:<");
            ini = a.indexOf("<img src=\"", ini);
            int fin = a.indexOf("\"", ini + "<img src=\"".length() + 1);
            String img_path_flag = a.substring(ini + "<img src=\"".length(), fin);
            //set
            DescriptionMtgInfo descriptionMtgInfo = new DescriptionMtgInfo();
            descriptionMtgInfo.imgPath = img_path_flag;
            cardinfo.lstDescription.add(descriptionMtgInfo);
            //set Idx
            this.mIdx = cardinfo.lstDescription.size() - 1;
            //send
            sendMessage(IMG_DESCRIPTION_OK, cardinfo, mIdx);
            //endregion
            //region language name
            ini = a.indexOf("alt=\"", fin);
            fin = a.indexOf("\"", ini + "alt=\"".length() + 1);
            String language = a.substring(ini + "alt=\"".length(), fin);
            //set
            cardinfo.lstDescription.get(mIdx).languague = language;
            //send
            sendMessage(NAME_LANG_DESCRIPTION_OK, cardinfo, mIdx);
            //endregion
            //region Relative path to language Description
            ini = a.indexOf("<a href=\"", fin);
            fin = a.indexOf("\"", ini + "<a href=\"".length() + 1);
            String urlDesc1 = "http://magiccards.info" + a.substring(ini + "<a href=\"".length(), fin);
            lstUrlLang.add(urlDesc1);
            //endregion
            //region name card transtaled
            ini = a.indexOf(">", fin);
            fin = a.indexOf("<", ini);
            String namecard_translated = a.substring(ini + 1, fin);
            //set
            cardinfo.lstDescription.get(mIdx).name = namecard_translated;
            //send
            sendMessage(NAME_CARD_DESCRIPTION_OK, cardinfo, mIdx);
            //endregion
            //endregion
            int fin_first_fin=fin;
            int fin_de_idiomas = a.indexOf("<br><br>\n", fin);
            //region find Next Descriptions
            boolean are_more_lang = true;
            while (are_more_lang) {
                //region get img banderita
                ini = a.indexOf("<img src=\"", fin);
                if (ini > fin_de_idiomas || fin == -1) break;
                fin = a.indexOf("\"", ini + "<img src=\"".length() + 1);
                if(fin<fin_first_fin) break;
                img_path_flag = a.substring(ini + "<img src=\"".length(), fin);
                //set
                descriptionMtgInfo = new DescriptionMtgInfo();
                descriptionMtgInfo.imgPath = img_path_flag;
                cardinfo.lstDescription.add(descriptionMtgInfo);
                //set Idx
                this.mIdx = cardinfo.lstDescription.size() - 1;
                //send
                sendMessage(IMG_DESCRIPTION_OK, cardinfo, mIdx);
                //endregion
                //region language name
                ini = a.indexOf("alt=\"", fin);
                fin = a.indexOf("\"", ini + "alt=\"".length() + 1);
                language = a.substring(ini + "alt=\"".length(), fin);
                //set
                cardinfo.lstDescription.get(mIdx).languague = language;
                 //send
                    sendMessage(NAME_LANG_DESCRIPTION_OK, cardinfo, mIdx);//send
                //endregion
                //region Relative path to language Description
                ini = a.indexOf("<a href=\"", fin);
                fin = a.indexOf("\"", ini + "<a href=\"".length() + 1);
                urlDesc1 = "http://magiccards.info" + a.substring(ini + "<a href=\"".length(), fin);
                lstUrlLang.add(urlDesc1);
                //endregion
                //region name card transtaled
                ini = a.indexOf(">", fin);
                fin = a.indexOf("<", ini);
                namecard_translated = a.substring(ini + 1, fin);
                //set
                cardinfo.lstDescription.get(mIdx).name = namecard_translated;
                //send
                sendMessage(NAME_CARD_DESCRIPTION_OK, cardinfo, mIdx);
                //endregion
            }
            //endregion

            for (int i = 0; i < lstUrlLang.size() - 1; i++) {
                getUrlDescFromMtgInfo(lstUrlLang.get(i), cardinfo, i, lstUrlLang.size() - 1);
            }
            //endregion

        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(ERROR, cardinfo);
        }
    }

    private void getUrlDescFromMtgInfo(String url, final CardInfo cardinfo, final int idx, final int lastDesc) throws Exception {

        Ion.with(this.mContext.getApplicationContext()).load(url).asString().setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String a) {
                try {
                    //region get
                    int ini = 0;
                    if (a.indexOf("class=\"flag\">") > 0)
                        ini = a.indexOf("class=\"flag\">");
                    else
                        ini = a.indexOf("class=\"flag2\">");

                    ini = a.indexOf("<p>", ini);
                    int fin = a.indexOf("</p>", ini);
                    String typeCard = a.substring(ini + "<p>".length(), fin).replace("\n", " ").replaceAll("<[^>]*>", "");

                    ini = a.indexOf("<p class=\"ctext\">", fin);
                    if (a.indexOf("</i></p>", ini) > 0)
                        fin = a.indexOf("</i></p>", ini);
                    else
                        fin = a.indexOf("</p>", ini);

                    String description = a.substring(ini, fin).replaceAll("<[^>]*>", "");
                    //endregion
                    //set
                    cardinfo.lstDescription.get(idx).description = typeCard + "\n" + description;
                    //send
                    sendMessage(DESC_DESCRIPTION_OK, cardinfo, idx);

                    if (idx == lastDesc)
                        sendMessage(ALL_DATA_COMPLETE, cardinfo);

                } catch (Exception e1) {
                    e1.printStackTrace();
                    sendMessage(ERROR, cardinfo, idx);
                }
            }
        });
    }

    private void getImgCardFromMtgInfo(String a, final CardInfo cardinfo) throws Exception {
        //get
        int ini = a.indexOf("src=\"http://partner.tcgplayer.com/x3/mchl.ashx");
        ini = a.indexOf("<img src=\"", ini);
        int fin = a.indexOf("\"", ini + "<img src=\"".length() + 1);
        String imgPath = a.substring(ini + "<img src=\"".length(), fin);
        //set
        cardinfo.setImgPath(imgPath);
        //send
        sendMessage(IMG_OK, cardinfo);
    }

    private void getPriceFromMtgInfo(String a, final CardInfo cardinfo) throws Exception {
        int ini = a.indexOf("src=\"http://partner.tcgplayer.com/");
        int fin = a.indexOf("\"><", ini);
        String urlPriceInfo = a.substring(ini + "src=\"".length(), fin).replace("amp;", "");
        Ion.with(this.mContext.getApplicationContext()).load(urlPriceInfo).asString().setCallback(new FutureCallback<String>() {
            @Override
            public void onCompleted(Exception e, String a) {
                //get
                int f_ini = a.indexOf("id=\"TCGPHiLoTable\"");
                int ini = a.indexOf("$", f_ini);
                int fin = a.indexOf("<", ini);
                String priceL = a.substring(ini, fin);

                ini = a.indexOf("$", fin);
                fin = a.indexOf("<", ini);
                String priceM = a.substring(ini, fin);

                ini = a.indexOf("$", fin);
                fin = a.indexOf("<", ini);
                String priceH = a.substring(ini, fin);

                //set
                cardinfo.setPriceL(priceL);
                cardinfo.setPriceM(priceM);
                cardinfo.setPriceH(priceH);
                cardinfo.setPrice("L: " + priceL + " M: " + priceM + " H: " + priceH);
                //send
                sendMessage(PRICE_OK, cardinfo);
            }
        });


    }


}
