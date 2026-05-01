package com.etl.generated.job.xmlnestedtocsvtagvalidation.source;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class TVLAccountDetails {

    public TVLAccountDetails() {}

    @XmlElement(name = "AccountNumber")
    private String AccountNumber;

    @XmlElement(name = "FleetIndicator")
    private String FleetIndicator;

    public String getAccountNumber() {
        return AccountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.AccountNumber = accountNumber;
    }

    public String getFleetIndicator() {
        return FleetIndicator;
    }

    public void setFleetIndicator(String fleetIndicator) {
        this.FleetIndicator = fleetIndicator;
    }
}

