package com.etl.generated.job.xmlnestedtocsvtagvalidation.source;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class TVLPlateDetails {

    public TVLPlateDetails() {}

    @XmlElement(name = "PlateCountry")
    private String PlateCountry;

    @XmlElement(name = "PlateState")
    private String PlateState;

    @XmlElement(name = "PlateNumber")
    private String PlateNumber;

    public String getPlateCountry() {
        return PlateCountry;
    }

    public void setPlateCountry(String plateCountry) {
        this.PlateCountry = plateCountry;
    }

    public String getPlateState() {
        return PlateState;
    }

    public void setPlateState(String plateState) {
        this.PlateState = plateState;
    }

    public String getPlateNumber() {
        return PlateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.PlateNumber = plateNumber;
    }
}

