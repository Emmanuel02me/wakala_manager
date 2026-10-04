package com.Wakala.v1.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidTransactionRequest(
                @NotBlank @Size(max = 255) String reason) {
}