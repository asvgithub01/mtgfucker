package io.asv.mtgocr.ocrreader.model;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created by Alberto on 16/10/2016.
 */
public class Biblio implements Serializable {
    /**
     * The collection has been serialized since the original 2016 version. Keep its historical
     * identifier forever: adding a helper method must never make thousands of saved cards
     * unreadable.
     */
    private static final long serialVersionUID = -2428653643628406405L;

    public String nameFile;
    private String name;
    public ArrayList<CardInfo> cards;

    public Biblio(String nameFile, String name) {
        this.nameFile = nameFile;
        this.name = name;
        this.cards = new ArrayList<CardInfo>();
    }

    public String getName() {
        return name;
    }

    public void addCard(String name, String price, String description, String imgPath, String quantity) {
        this.cards.add(new CardInfo(name, price, description, imgPath, quantity));
    }
    public void addCard(CardInfo cardInfo){
        this.cards.add(cardInfo);
    }

    /** Returns a detached graph that background serialization can traverse safely. */
    public Biblio snapshotForPersistence() {
        Biblio copy = new Biblio(nameFile, name);
        copy.cards.ensureCapacity(cards == null ? 0 : cards.size());
        if (cards != null) {
            for (CardInfo card : cards) {
                copy.cards.add(card == null ? null : card.snapshotForPersistence());
            }
        }
        return copy;
    }
}
