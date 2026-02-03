package com.grouppay.settlement.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserBalance {
    private Long userId;
    private BigDecimal balance; // Positive = gets money, Negative = owes money
}
