package io.asv.mtgocr.ocrreader.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alberto on 16/10/2016.
 */
public class CardInfo implements Serializable {
    private String name;
    private String price;
    private String description;
    private String imgPath;
    private String quantity;
    /**/
    public String getPriceL() {
        return priceL;
    }

    public void setPriceL(String priceL) {
        this.priceL = priceL;
    }

    public String getPriceM() {
        return priceM;
    }

    public void setPriceM(String priceM) {
        this.priceM = priceM;
    }

    public String getPriceH() {
        return priceH;
    }

    public void setPriceH(String priceH) {
        this.priceH = priceH;
    }


    private String priceL;
    private String priceM;
    private String priceH;
    //todo extra de chapu esto, pa probar vale, xo lo suyo es una
    //clase DescritionMtgInfo() con descripcion, imgbanderita, name y aki tener un List<Descrip...>
    public List<DescriptionMtgInfo> lstDescription = new ArrayList<DescriptionMtgInfo>();

    public DescriptionMtgInfo lastDescriptionMtgInfoItem() throws Exception {
        return lstDescription.get(lstDescription.size() - 1);
    }

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

    public String toJSON() {
        return "{\"name\":\"" + this.name + "\"," +
                "\"price\":\"" + this.price + "\"," +
                "\"description\":\"" + this.description + "\"," +
                "\"imgPath\":\"" + this.imgPath + "\"," +
                "\"quantity\":\"" + this.quantity + "\"}";
    }

}
