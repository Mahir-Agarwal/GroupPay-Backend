package com.grouppay.expense.application;

import com.grouppay.expense.domain.Expense;
import com.grouppay.expense.infrastructure.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetExpensesService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public List<Expense> getExpensesByGroup(Long groupId) {
        return expenseRepository.findByGroupId(groupId);
    }
}
