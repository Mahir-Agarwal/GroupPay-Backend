package com.grouppay.settlement.application;

import com.grouppay.expense.domain.Expense;
import com.grouppay.expense.domain.ExpenseSplit;
import com.grouppay.expense.infrastructure.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BalanceCalculationService {

    private final ExpenseRepository expenseRepository;

    /**
     * Calculates the net balance for each user in the group based on all recorded expenses.
     * <p>
     * Logic:
     * <ul>
     *     <li><b>Credit:</b> The payer is credited with the full expense amount (as they paid it).</li>
     *     <li><b>Debit:</b> Each user involved in the split (including the payer if applicable) is debited their split amount.</li>
     * </ul>
     * Result = Total Paid - Total Consumed.
     * Positive balance means the user is owed money. Negative balance means the user owes money.
     * </p>
     *
     * @param groupId The ID of the group to calculate balances for.
     * @return A map of User ID to their Net Balance (BigDecimal).
     */
    public Map<Long, BigDecimal> calculateNetBalances(Long groupId) {
        List<Expense> expenses = expenseRepository.findByGroupId(groupId);
        Map<Long, BigDecimal> netBalances = new HashMap<>();

        for (Expense expense : expenses) {
            Long payerId = expense.getPaidBy().getId();
            
            // Payer gets back the total amount (technically they paid it, so others owe them)
            // Wait, usually: Payer pays 100. Split A:50, B:50.
            // Payer is +50 (since they paid 100 but kept 50 worth of value? No.)
            // Logic:
            // Net Balance = Paid - Owed
            // Payer pays 100.
            // Split: A owes 50, B owes 50.
            // A's Balance: -50 (Owe)
            // B's Balance: -50 (Owe)
            // Payer's Balance: +100 (Paid) - 0 (if not in split) ??
            // If Payer is also A?
            // Payer pays 100. Split A(Payer): 50, B: 50.
            // A Paid: 100. A Owes: 50. Net: +50.
            // B Paid: 0. B Owes: 50. Net: -50.
            // Check correct logic.

            // 1. Credit the payer with the full amount
            netBalances.merge(payerId, expense.getAmount(), BigDecimal::add);

            // 2. Debit the borrowers (splits)
            for (ExpenseSplit split : expense.getSplits()) {
                Long borrowedById = split.getUser().getId();
                netBalances.merge(borrowedById, split.getAmount().negate(), BigDecimal::add);
            }
        }
        return netBalances;
    }
}
