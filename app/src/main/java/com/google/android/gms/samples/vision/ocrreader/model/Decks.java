package com.google.android.gms.samples.vision.ocrreader.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alberto on 16/10/2016.
 */
public class Decks implements Serializable{
    public String nameFile="myDecks.json";
    public List<Deck> decks;

    public Decks() {
        this.decks = new ArrayList<Deck>();
    }
    public void addDeck(String nameFile, String name) {
        this.decks.add(new Deck(nameFile, name));
    }
    public void addDeck(Deck deck) {
        this.decks.add(deck);
    }

}
