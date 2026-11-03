package com.northwind.claims.documents;

import com.northwind.claims.model.ClaimType;
import java.util.List;

public final class DocumentChecklist {
    private DocumentChecklist() {}

    public static List<String> required(ClaimType type, long amountCents) {
        List<String> docs = new java.util.ArrayList<>(List.of("claim form", "photo of damage"));
        switch (type) {
            case AUTO -> docs.add("police report or accident report");
            case THEFT -> docs.add("police report");
            case WATER_DAMAGE -> docs.add("plumber or contractor report");
            case PROPERTY -> { }
        }
        if (amountCents >= 1_000_000) {
            docs.add("two repair estimates");
        }
        return docs;
    }
}
