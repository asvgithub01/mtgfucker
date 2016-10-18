package com.google.android.gms.samples.vision.ocrreader.model;

import java.io.Serializable;

/**
 * Created by Alberto on 17/10/2016.
 */
public class DescriptionMtgInfo implements Serializable{
    public String name;
    public String imgPath;
    public String description;
    public String languague;

    public DescriptionMtgInfo() {
        this.name="";
        this.imgPath="";
        this.description="";
        this.languague="";
    }
}
