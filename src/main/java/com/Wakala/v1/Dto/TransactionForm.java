//thymeleaf

package com.Wakala.v1.Dto;

import com.Wakala.v1.Entity.Transaction;
import java.math.BigDecimal;

public class TransactionForm {

    private Long providerId;
    private Long destinationProviderId;
    private Transaction.TransactionType transactionType;
    private BigDecimal amount = BigDecimal.ZERO;
    private Boolean hasNetworkFee = true;
    private Boolean chargeOwnerCommission = false;
    private String customerPhone;
    private String customerName;
    private String reference;

    // Getters na Setters
    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Long getDestinationProviderId() {
        return destinationProviderId;
    }

    public void setDestinationProviderId(Long destinationProviderId) {
        this.destinationProviderId = destinationProviderId;
    }

    public Transaction.TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(Transaction.TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Boolean getHasNetworkFee() {
        return hasNetworkFee;
    }

    public void setHasNetworkFee(Boolean hasNetworkFee) {
        this.hasNetworkFee = hasNetworkFee;
    }

    public Boolean getChargeOwnerCommission() {
        return chargeOwnerCommission;
    }

    public void setChargeOwnerCommission(Boolean chargeOwnerCommission) {
        this.chargeOwnerCommission = chargeOwnerCommission;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}