package com.google.android.gms.samples.vision.ocrreader.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by Alberto on 16/10/2016.
 */
public class CardInfo implements Serializable {


    private String name;
    private String price;
    private String description;
    private String imgPath;
    private String quantity;

    public CardInfo(String name, String price, String description, String imgPath, String quantity) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.imgPath = imgPath;
        this.quantity = quantity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImgPath() {
        return imgPath;
    }

    public void setImgPath(String imgPath) {
        this.imgPath = imgPath;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String toJSON(){
      return  "{\"name\":\""+this.name+"\","+
        "\"price\":\""+this.price+"\","+
        "\"description\":\""+this.description+"\","+
        "\"imgPath\":\""+this.imgPath+"\","+
        "\"quantity\":\""+this.quantity+"\"}";
   }

}
