/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.asv.mtgocr.ocrreader;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;

import com.google.android.gms.common.api.CommonStatusCodes;

/**
 * Main activity demonstrating how to pass extra parameters to an activity that
 * recognizes text.
 */
public class MainActivity extends Activity implements View.OnClickListener {

    // Use a compound button so either checkbox or switch widgets work.
    private CompoundButton autoFocus;
    private CompoundButton useFlash;
    private static final int RC_OCR_CAPTURE = 9003;
    private static final String TAG = "MainActivity";

    //Button btnBiblio,btnNewDeck,btnEditDecks;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        autoFocus = (CompoundButton) findViewById(R.id.auto_focus);
        useFlash = (CompoundButton) findViewById(R.id.use_flash);

        findViewById(R.id.btnBiblio).setOnClickListener(this);
        findViewById(R.id.btnNewDeck).setOnClickListener(this);
        findViewById(R.id.btnEditDecks).setOnClickListener(this);


    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnBiblio) {
            // launch Ocr capture activity.
            Intent intent = new Intent(this, OcrCaptureActivity.class);
            intent.putExtra(App.INTENT_AUTO_FOCUS, autoFocus.isChecked());
            intent.putExtra(App.INTENT_USE_FLASH, useFlash.isChecked());
            intent.putExtra(App.INTENT_PERSISTOR_MODE, "0");

            startActivityForResult(intent, RC_OCR_CAPTURE);
        }

        if (v.getId() == R.id.btnNewDeck) {
            // launch Ocr capture activity.
            Intent intent = new Intent(this, OcrCaptureActivity.class);
            intent.putExtra(App.INTENT_AUTO_FOCUS, autoFocus.isChecked());
            intent.putExtra(App.INTENT_USE_FLASH, useFlash.isChecked());
            intent.putExtra(App.INTENT_PERSISTOR_MODE, "1");

            startActivityForResult(intent, RC_OCR_CAPTURE);
        }
        if (v.getId() == R.id.btnEditDecks) {
            // launch Ocr capture activity.
            Intent intent = new Intent(this, OcrCaptureActivity.class);
            intent.putExtra(App.INTENT_AUTO_FOCUS, autoFocus.isChecked());
            intent.putExtra(App.INTENT_USE_FLASH, useFlash.isChecked());
            intent.putExtra(App.INTENT_PERSISTOR_MODE, "2");

            startActivityForResult(intent, RC_OCR_CAPTURE);
        }
    }

    /**
     * Called when an activity you launched exits, giving you the requestCode
     * you started it with, the resultCode it returned, and any additional
     * DataUtils from it.  The <var>resultCode</var> will be
     * {@link #RESULT_CANCELED} if the activity explicitly returned that,
     * didn't return any result, or crashed during its operation.
     * <p/>
     * <p>You will receive this call immediately before onResume() when your
     * activity is re-starting.
     * <p/>
     *
     * @param requestCode The integer request code originally supplied to
     * startActivityForResult(), allowing you to identify who this
     * result came from.
     * @param resultCode  The integer result code returned by the child activity
     * through its setResult().
     * @param DataUtils        An Intent, which can return result DataUtils to the caller
     * (various DataUtils can be attached to Intent "extras").
     * @see #startActivityForResult
     * @see #createPendingResult
     * @see #setResult(int)
     */
    String text;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_OCR_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Log.d(TAG, "Text read: " + text);
                } else {
                    Log.d(TAG, "No Text captured, intent DataUtils is null");
                }
            } else {
                Log.e(TAG, String.format(getString(R.string.ocr_error),
                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


}
