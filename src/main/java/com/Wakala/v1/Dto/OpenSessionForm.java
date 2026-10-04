//Thymeleaf 2/10/2026
package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OpenSessionForm {
    private BigDecimal openingCash = BigDecimal.ZERO;
    private String adjustmentNote;
    private String notes;

    public BigDecimal getOpeningCash() {
        return openingCash;
    }

    public void setOpeningCash(BigDecimal openingCash) {
        this.openingCash = openingCash;
    }

    public String getAdjustmentNote() {
        return adjustmentNote;
    }

    public void setAdjustmentNote(String adjustmentNote) {
        this.adjustmentNote = adjustmentNote;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    private List<FloatOpeningForm> floatOpenings = new ArrayList<>();

    public List<FloatOpeningForm> getFloatOpenings() {
        return floatOpenings;
    }

    public void setFloatOpenings(List<FloatOpeningForm> floatOpenings) {
        this.floatOpenings = floatOpenings;
    }
}