package com.Wakala.v1.Dto;

import jakarta.validation.constraints.NotNull;

public record ApproveExpenseRequest(
        @NotNull Long expenseId,
        @NotNull Boolean approved) {
}
