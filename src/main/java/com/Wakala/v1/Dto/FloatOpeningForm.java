//Thymeleaf 2/10/2026

package com.Wakala.v1.Dto;

import java.math.BigDecimal;

public class FloatOpeningForm {
    private Long providerId;
    private BigDecimal openingBalance = BigDecimal.ZERO;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }
}