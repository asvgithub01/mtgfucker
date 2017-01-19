package io.asv.mtgocr.ocrreader.data;

import io.asv.mtgocr.ocrreader.model.CardInfo;

/**
 * Created by Alberto on 25/10/2016.
 */

public interface IDataProvider {

    public void GetCardInfo(String name_card, final CardInfo cardInfo);

}
