package com.Wakala.v1.Dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExpenseRequest(
        @NotNull Long sessionId,
        @NotBlank String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String category,
        String receiptNumber) {
}
