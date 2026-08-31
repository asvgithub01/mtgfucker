package io.asv.mtgocr.ocrreader.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Alberto on 16/10/2016.
 */
public class CardInfo implements Serializable {
    // Keep compatibility with collections serialized by versions <= 1.x.
    private static final long serialVersionUID = -7899312800426509165L;
    private String name;
    private String price;
    private String description;
    private String imgPath;
    private String quantity;
    private String collectionItemId;
    private long addedAt;
    private String printingUuid;
    private String setCode;
    private String setName;
    private String collectorNumber;
    private String finish;
    private String condition;
    private ArrayList<String> personalCollections;
    private ArrayList<String> decks;
    private ArrayList<String> sideboardDecks;
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
        this.collectionItemId = UUID.randomUUID().toString();
        this.addedAt = System.currentTimeMillis();
        this.personalCollections = new ArrayList<String>();
        this.decks = new ArrayList<String>();
        this.sideboardDecks = new ArrayList<String>();
    }

    public String getCollectionItemId() {
        ensureCollectionItemId();
        return collectionItemId;
    }

    public boolean ensureCollectionItemId() {
        if (collectionItemId == null || collectionItemId.length() == 0) {
            collectionItemId = UUID.randomUUID().toString();
            return true;
        }
        return false;
    }

    public long getAddedAt() {
        return addedAt;
    }

    public boolean ensureAddedAt(long fallback) {
        if (addedAt <= 0L) {
            addedAt = fallback;
            return true;
        }
        return false;
    }

    public void setAddedAt(long addedAt) {
        this.addedAt = addedAt;
    }

    public String getPrintingUuid() {
        return printingUuid;
    }

    public void setPrintingUuid(String printingUuid) {
        this.printingUuid = printingUuid;
    }

    public String getSetCode() {
        return setCode;
    }

    public void setSetCode(String setCode) {
        this.setCode = setCode;
    }

    public String getSetName() {
        return setName;
    }

    public void setSetName(String setName) {
        this.setName = setName;
    }

    public String getCollectorNumber() {
        return collectorNumber;
    }

    public void setCollectorNumber(String collectorNumber) {
        this.collectorNumber = collectorNumber;
    }

    public String getFinish() {
        return finish;
    }

    public void setFinish(String finish) {
        this.finish = finish;
    }

    public String getCondition() {
        return CardCondition.normalize(condition);
    }

    public void setCondition(String condition) {
        this.condition = CardCondition.normalize(condition);
    }

    public ArrayList<String> getPersonalCollections() {
        if (personalCollections == null) personalCollections = new ArrayList<String>();
        return personalCollections;
    }

    /**
     * Every CardInfo already belongs to the personal Biblio. The legacy serialized field now
     * represents optional user-defined groups so old collection files remain compatible.
     */
    public ArrayList<String> getGroups() {
        return getPersonalCollections();
    }

    public ArrayList<String> getDecks() {
        if (decks == null) decks = new ArrayList<String>();
        return decks;
    }

    public ArrayList<String> getSideboardDecks() {
        if (sideboardDecks == null) sideboardDecks = new ArrayList<String>();
        return sideboardDecks;
    }

    public boolean isSideboardForDeck(String name) {
        return getSideboardDecks().contains(name);
    }

    public boolean addPersonalCollection(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.length() == 0 || getPersonalCollections().contains(normalized)) return false;
        return getPersonalCollections().add(normalized);
    }

    public boolean addGroup(String name) {
        return addPersonalCollection(name);
    }

    public boolean addDeck(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.length() == 0 || getDecks().contains(normalized)) return false;
        return getDecks().add(normalized);
    }

    /** Assigns every physical copy represented by this row to the chosen deck zone. */
    public boolean setDeckZone(String name, boolean sideboard) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.length() == 0) return false;
        boolean changed = addDeck(normalized);
        if (sideboard) {
            if (!getSideboardDecks().contains(normalized)) changed |= getSideboardDecks().add(normalized);
        } else {
            changed |= getSideboardDecks().remove(normalized);
        }
        return changed;
    }

    public boolean removePersonalCollection(String name) {
        return getPersonalCollections().remove(name);
    }

    public boolean removeGroup(String name) {
        return removePersonalCollection(name);
    }

    public boolean removeDeck(String name) {
        boolean changed = getDecks().remove(name);
        changed |= getSideboardDecks().remove(name);
        return changed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrice() {
        return CardCondition.adjustedDisplay(price, priceM, getCondition());
    }

    /** Unadjusted Near Mint display value stored by the market-price provider. */
    public String getBasePrice() {
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

    /** Returns a safe physical-copy count for legacy collections with an empty quantity. */
    public int getQuantityCount() {
        try {
            return Math.max(1, Integer.parseInt(quantity == null ? "" : quantity.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public void setQuantityCount(int quantity) {
        this.quantity = String.valueOf(Math.max(1, quantity));
    }

    public boolean ensureQuantity() {
        String normalized = String.valueOf(getQuantityCount());
        if (!normalized.equals(quantity)) {
            quantity = normalized;
            return true;
        }
        return false;
    }

    public String toJSON() {
        return "{\"name\":\"" + this.name + "\"," +
                "\"price\":\"" + this.price + "\"," +
                "\"description\":\"" + this.description + "\"," +
                "\"imgPath\":\"" + this.imgPath + "\"," +
                "\"quantity\":\"" + this.quantity + "\"}";
    }

}
