package io.asv.mtgocr.ocrreader.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Alberto on 16/10/2016.
 */
public class Biblio implements Serializable {
    public String nameFile;
    private String name;
    public ArrayList<CardInfo> cards;

    public Biblio(String nameFile, String name) {
        this.nameFile = nameFile;
        this.name = name;
        this.cards = new ArrayList<CardInfo>();
    }

    public void addCard(String name, String price, String description, String imgPath, String quantity) {
        this.cards.add(new CardInfo(name, price, description, imgPath, quantity));
    }
    public void addCard(CardInfo cardInfo){
        this.cards.add(cardInfo);
    }
}
