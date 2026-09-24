package com.neueda.leap.domain;

/**
 * Domain model for the `instruments` table.
 */
public class Instrument {
    private Integer instrumentId;
    private String instrumentName;
    private String ticker;
    private String currency;
    private String assetClass;
    private String securityType;
    private Boolean isActive;
    public Integer getInstrumentId() {
        return instrumentId;
    }
    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }
    public String getInstrumentName() {
        return instrumentName;
    }
    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }
    public String getTicker() {
        return ticker;
    }
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
    public String getCurrency() {
        return currency;
    }
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    public String getAssetClass() {
        return assetClass;
    }
    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }
    public String getSecurityType() {
        return securityType;
    }
    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }
    public Boolean getIsActive() {
        return isActive;
    }
    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
