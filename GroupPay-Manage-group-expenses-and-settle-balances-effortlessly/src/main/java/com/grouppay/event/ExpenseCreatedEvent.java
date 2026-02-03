package com.grouppay.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ExpenseCreatedEvent {
    private Long expenseId;
    private Long groupId;
    private BigDecimal amount;
    private Long payerId;
}
