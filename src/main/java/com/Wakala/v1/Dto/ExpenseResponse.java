package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseResponse(
    Long id, 
    String description, 
    BigDecimal amount,
    String category, 
    String status, 
    LocalDateTime createdAt
) {}
