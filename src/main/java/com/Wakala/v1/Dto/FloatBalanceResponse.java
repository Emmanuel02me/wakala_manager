package com.Wakala.v1.Dto;

import java.math.BigDecimal;

public record FloatBalanceResponse(
    Long id, 
    String providerName,
    BigDecimal openingBalance, 
    BigDecimal closingBalance,
    BigDecimal expectedClosing, 
    BigDecimal difference
) {}