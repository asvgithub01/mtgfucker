package io.asv.mtgocr.ocrreader.model;

import java.io.Serializable;

public final class DeckDefinition implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String formatId;
    private long createdAt;

    public DeckDefinition(String name, String formatId, long createdAt) {
        this.name = name;
        this.formatId = formatId;
        this.createdAt = createdAt;
    }

    public String getName() { return name == null ? "" : name; }
    public String getFormatId() { return formatId == null ? "free" : formatId; }
    public long getCreatedAt() { return createdAt; }
    public void setFormatId(String formatId) { this.formatId = formatId; }
}
