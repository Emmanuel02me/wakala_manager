//Thymeleaf 2/10/2026

package com.Wakala.v1.Dto;

import java.math.BigDecimal;

public class FloatClosingForm {
    private Long providerId;
    private BigDecimal closingBalance = BigDecimal.ZERO;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }
}