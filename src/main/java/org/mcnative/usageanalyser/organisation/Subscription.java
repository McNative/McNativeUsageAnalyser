package org.mcnative.usageanalyser.organisation;

import net.pretronic.libraries.utility.Validate;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Subscription {

    private final String organisationId;
    private final BigDecimal revenue;
    private final BigDecimal hourlyRevenue;

    public Subscription(String organisationId, BigDecimal revenue) {
        Validate.notNull(organisationId, "OrganisationId can't be null");
        Validate.notNull(revenue, "Revenue can't be null");
        this.organisationId = organisationId;
        this.revenue = revenue;
        this.hourlyRevenue = revenue.divide(new BigDecimal(720), 4, RoundingMode.DOWN);//@Todo add rounding mode
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public BigDecimal getHourlyRevenue() {
        return this.hourlyRevenue;
    }
}
