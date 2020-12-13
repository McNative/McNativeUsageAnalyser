package org.mcnative.usageanalyser;

import java.math.BigDecimal;

public class Resource {

    private final String id;
    private final String ownerId;
    private final BigDecimal priceRating;

    public Resource(String id, String ownerId, BigDecimal priceRating) {
        this.id = id;
        this.ownerId = ownerId;
        this.priceRating = priceRating;
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public BigDecimal getPriceRating() {
        return priceRating;
    }
}
