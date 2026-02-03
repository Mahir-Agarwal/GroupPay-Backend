package com.grouppay.expense.api;

import com.grouppay.expense.application.AddExpenseService;
import com.grouppay.expense.domain.Expense;
import com.grouppay.expense.domain.ExpenseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final AddExpenseService addExpenseService;
    private final com.grouppay.expense.application.GetExpensesService getExpensesService;
    private final com.grouppay.expense.application.DeleteExpenseService deleteExpenseService;

    /**
     * Creates a new expense within a group.
     * <p>
     * Endpoint: POST /expenses
     * </p>
     *
     * @param userId      ID of the payer.
     * @param groupId     ID of the group.
     * @param description Description of expense.
     * @param amount      Total amount (must be positive).
     * @param type        Splitting strategy (EQUAL, EXACT, PERCENTAGE).
     * @param splits      Optional JSON body map for custom splits (UserId -> Value).
     * @return The created Expense object.
     */
    @PostMapping
    public ResponseEntity<Expense> addExpense(
            @RequestParam Long userId,
            @RequestParam Long groupId,
            @RequestParam String description,
            @RequestParam BigDecimal amount,
            @RequestParam ExpenseType type,
            @RequestParam(required = false) String upiId,
            @RequestBody(required = false) Map<Long, BigDecimal> splits // Key: UserId, Value: Amount (for exact split)
    ) {
        return ResponseEntity.ok(addExpenseService.addExpense(userId, groupId, description, amount, type, splits, upiId));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<java.util.List<Expense>> getGroupExpenses(@PathVariable Long groupId) {
        return ResponseEntity.ok(getExpensesService.getExpensesByGroup(groupId));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long expenseId) {
        deleteExpenseService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
    }
}
