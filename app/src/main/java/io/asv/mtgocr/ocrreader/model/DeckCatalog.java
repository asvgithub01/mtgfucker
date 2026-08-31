package io.asv.mtgocr.ocrreader.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class DeckCatalog implements Serializable {
    private static final long serialVersionUID = 1L;
    public String nameFile = "myDeckCatalog.Json";
    public List<DeckDefinition> decks = new ArrayList<>();
    public boolean legacyGroupsMigrated;
}
