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

import android.util.SparseArray;
import android.graphics.Rect;

import io.asv.mtgocr.ocrreader.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * A very simple Processor which receives detected TextBlocks and adds them to the overlay
 * as OcrGraphics.
 */
public class OcrDetectorProcessor implements Detector.Processor<TextBlock> {

    private GraphicOverlay<OcrGraphic> mGraphicOverlay;
    private final TextCandidateListener listener;

    interface TextCandidateListener {
        void onTextCandidates(List<String> candidates);
    }

    OcrDetectorProcessor(GraphicOverlay<OcrGraphic> ocrGraphicOverlay,
        TextCandidateListener listener) {
        mGraphicOverlay = ocrGraphicOverlay;
        this.listener = listener;
    }

    /**
     * Called by the detector to deliver detection results.
     * If your application called for it, this could be a place to check for
     * equivalent detections by tracking TextBlocks that are similar in location and content from
     * previous frames, or reduce noise by eliminating TextBlocks that have not persisted through
     * multiple detections.
     */
    @Override
    public void receiveDetections(Detector.Detections<TextBlock> detections) {
        mGraphicOverlay.clear();
        SparseArray<TextBlock> items = detections.getDetectedItems();
        List<String> candidates = new ArrayList<>();
        int width = detections.getFrameMetadata().getWidth();
        int height = detections.getFrameMetadata().getHeight();
        int rotation = detections.getFrameMetadata().getRotation();
        if ((rotation & 1) == 1) {
            int swapped = width;
            width = height;
            height = swapped;
        }
        OcrTitleRegion.Bounds titleRegion = OcrTitleRegion.forFrame(width, height);
        for (int i = 0; i < items.size(); ++i) {
            TextBlock item = items.valueAt(i);
            boolean acceptedBlock = false;
            List<? extends Text> components = item.getComponents();
            if (components != null) {
                for (Text component : components) {
                    Rect box = component.getBoundingBox();
                    if (box == null || !titleRegion.containsCenter(box.left, box.top, box.right, box.bottom)) {
                        continue;
                    }
                    acceptedBlock = true;
                    addCandidate(candidates, component.getValue());
                }
            }
            if (!acceptedBlock && (components == null || components.isEmpty())) {
                Rect box = item.getBoundingBox();
                if (box != null && titleRegion.containsCenter(box.left, box.top, box.right, box.bottom)) {
                    acceptedBlock = true;
                    addCandidate(candidates, item.getValue());
                }
            }
            if (acceptedBlock) mGraphicOverlay.add(new OcrGraphic(mGraphicOverlay, item));
        }
        if (listener != null && !candidates.isEmpty()) listener.onTextCandidates(candidates);
    }

    private static void addCandidate(List<String> candidates, String rawText) {
        if (rawText == null) return;
        String[] lines = rawText.split("\\r\\n|\\r|\\n");
        for (String line : lines) {
            String cleaned = line.replace("|", "").trim();
            if (cleaned.length() >= 2 && cleaned.length() <= 80) candidates.add(cleaned);
        }
    }

    /**
     * Frees the resources associated with this detection processor.
     */
    @Override
    public void release() {
        mGraphicOverlay.clear();
    }
}
