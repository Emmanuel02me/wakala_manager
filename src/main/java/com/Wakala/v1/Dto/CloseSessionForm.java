//Thymeleaf 2/10/2026

package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CloseSessionForm {
    private BigDecimal closingCash = BigDecimal.ZERO;
    private String notes;

    public BigDecimal getClosingCash() {
        return closingCash;
    }

    public void setClosingCash(BigDecimal closingCash) {
        this.closingCash = closingCash;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    private List<FloatClosingForm> floatClosings = new ArrayList<>();

    public List<FloatClosingForm> getFloatClosings() {
        return floatClosings;
    }

    public void setFloatClosings(List<FloatClosingForm> floatClosings) {
        this.floatClosings = floatClosings;
    }
}