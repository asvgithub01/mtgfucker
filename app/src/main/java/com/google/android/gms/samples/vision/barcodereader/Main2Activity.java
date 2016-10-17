package com.google.android.gms.samples.vision.barcodereader;

import android.app.Activity;
import android.os.Bundle;

import com.google.android.gms.samples.vision.ocrreader.R;

import org.sufficientlysecure.htmltextview.HtmlAssetsImageGetter;
import org.sufficientlysecure.htmltextview.HtmlResImageGetter;
import org.sufficientlysecure.htmltextview.HtmlTextView;

public class Main2Activity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        HtmlTextView htmlTextView = (HtmlTextView) findViewById(R.id.html_text);
        //loads html from string and displays cat_pic.png from the app's assets folder
       /* htmlTextView.setHtml("<p><table id=\"TCGPHiLoTable\" cellspacing=\"0\" cellpadding=\"1\" border=\"0\"><tbody><tr><td class=\"TCGPHiLoLow\">L: <a href=\"http://store.tcgplayer.com/magic/darksteel/arcbound-ravager?partner=MAGCINFO\">$32.00</a></td><td class=\"TCGPHiLoMid\">M: <a href=\"http://store.tcgplayer.com/magic/darksteel/arcbound-ravager?partner=MAGCINFO\">$39.97</a></td><td class=\"TCGPHiLoHigh\">H: <a href=\"http://store.tcgplayer.com/magic/darksteel/arcbound-ravager?partner=MAGCINFO\">$62.65</a></td><td class=\"TCGPHiLoLink\"><a href=\"http://store.tcgplayer.com/magic/darksteel/arcbound-ravager?partner=MAGCINFO\">[Buy&nbsp;@&nbsp;TCGplayer]</a></td></tr></tbody></table></p>",
                new HtmlResImageGetter(htmlTextView));
        htmlTextView.setClickableTableSpan(new ClickableTableSpanImpl());
        DrawTableLinkSpan drawTableLinkSpan = new DrawTableLinkSpan();
        drawTableLinkSpan.setTableLinkText("[tap for table]");
        htmlTextView.setDrawTableLinkSpan(drawTableLinkSpan);*/

         htmlTextView.setHtml("<p>\n<table>\n" +
                         "    <tr>\n" +
                         "        <td>hola</td>\n" +
                         "        <td>hola1</td>\n" +
                         "        <td>hola2</td>\n" +
                         "    </tr>\n" +
                         "</table>\n</p>",
               new HtmlResImageGetter(htmlTextView));

         htmlTextView.setHtml(R.raw.table,  new HtmlResImageGetter(htmlTextView));

    }
}