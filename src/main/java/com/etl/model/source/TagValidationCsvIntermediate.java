package com.etl.model.source;

@SuppressWarnings("unused")
public class TagValidationCsvIntermediate {
    private String homeAgencyId;
    private String tagAgencyId;
    private String tagSerialNumber;
    private String plateNumber;
    private String plateCountry;
    private String plateState;
    private String accountNumber;

    public String getHomeAgencyId() {
        return homeAgencyId;
    }

    public void setHomeAgencyId(String homeAgencyId) {
        this.homeAgencyId = homeAgencyId;
    }

    public String getTagAgencyId() {
        return tagAgencyId;
    }

    public void setTagAgencyId(String tagAgencyId) {
        this.tagAgencyId = tagAgencyId;
    }

    public String getTagSerialNumber() {
        return tagSerialNumber;
    }

    public void setTagSerialNumber(String tagSerialNumber) {
        this.tagSerialNumber = tagSerialNumber;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getPlateCountry() {
        return plateCountry;
    }

    public void setPlateCountry(String plateCountry) {
        this.plateCountry = plateCountry;
    }

    public String getPlateState() {
        return plateState;
    }

    public void setPlateState(String plateState) {
        this.plateState = plateState;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}

