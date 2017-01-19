package io.asv.mtgocr.ocrreader;

import android.app.Application;

/**
 * Created by Alberto on 30/10/2016.
 */

public class App extends Application {
    public static final String INTENT_REQUEST_KEY = "RequestKey";
    public static final String INTENT_CARD_INFO = "Cardinfo";
    public static final String INTENT_IDX_DESC = "idxLstDesc";

    public static final String INTENT_AUTO_FOCUS = "AutoFocus";
    public static final String INTENT_USE_FLASH = "UseFlash";
    public static final String INTENT_PERSISTOR_MODE = "mtgModePersistor";
}
