package com.grouppay.expense.application;

import com.grouppay.expense.domain.Expense;
import com.grouppay.expense.domain.ExpenseSplit;
import com.grouppay.expense.domain.ExpenseType;
import com.grouppay.expense.infrastructure.ExpenseRepository;
import com.grouppay.group.domain.Group;
import com.grouppay.group.infrastructure.GroupRepository;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AddExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    // Event publisher placeholder
    private final com.grouppay.notification.application.NotificationService notificationService;

    /**
     * Adds a new expense to the group and calculates the splits among members.
     * <p>
     * This method validates the inputs (amount, description, payer membership),
     * creates an Expense entity, and determines how the amount should be split
     * based on the ExpenseType (EQUAL, EXACT, PERCENTAGE).
     * </p>
     *
     * @param userId      The ID of the user who paid for the expense.
     * @param groupId     The ID of the group where the expense is added.
     * @param description A brief description of the expense.
     * @param amount      The total amount of the expense. Must be positive.
     * @param type        The type of split (EQUAL, EXACT, PERCENTAGE).
     * @param splits      A map containing split details (User ID -> Amount or Percentage), required for EXACT/PERCENTAGE.
     * @return The saved Expense entity with all splits persisted.
     * @throws IllegalArgumentException if validation fails (e.g., non-member payer, invalid amount).
     * @throws RuntimeException if User or Group is not found.
     */
    @Transactional
    public Expense addExpense(Long userId, Long groupId, String description, BigDecimal amount, ExpenseType type, Map<Long, BigDecimal> splits, String upiId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be empty");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        boolean isPayerInGroup = group.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));
        if (!isPayerInGroup) {
            throw new IllegalArgumentException("Payer must be a member of the group");
        }

        Expense expense = Expense.builder()
                .description(description)
                .amount(amount)
                .paidBy(user)
                .group(group)
                .type(type)
                .upiId(upiId)
                .build();

        List<ExpenseSplit> expenseSplits = calculateSplits(expense, splits, group);
        expense.setSplits(expenseSplits);

        Expense savedExpense = expenseRepository.save(expense);
        
        // Notify all users involved in the split (except the payer)
        for (ExpenseSplit split : expenseSplits) {
            if (!split.getUser().getId().equals(userId)) {
                notificationService.createNotification(
                        split.getUser().getId(),
                        "New Expense Added",
                        user.getUsername() + " added '" + description + "' in " + group.getName(),
                        com.grouppay.notification.domain.NotificationType.EXPENSE
                );
            }
        }
        

        
        return savedExpense;
    }

    /**
     * Calculates the specific amount each user owes based on the ExpenseType.
     * 
     * <ul>
     *   <li><b>EQUAL:</b> Divides total amount by number of members. Adjusts remainders (pennies) to ensure exact sum.</li>
     *   <li><b>EXACT:</b> Uses provided amounts. Validates that the sum equals the total expense.</li>
     *   <li><b>PERCENTAGE:</b> Calculates amount based on percentage. Validates sum is 100%. Handles rounding to ensure total matches.</li>
     * </ul>
     *
     * @param expense   The expense entity (containing total amount).
     * @param splitData The input map of split details.
     * @param group     The group entity to access members for validation.
     * @return A list of ExpenseSplit entities ready to be saved.
     */
    private List<ExpenseSplit> calculateSplits(Expense expense, Map<Long, BigDecimal> splitData, Group group) {
        List<ExpenseSplit> splits = new ArrayList<>();
        List<User> groupMembers = group.getMembers().stream().map(m -> m.getUser()).toList();
        java.util.Set<Long> memberIds = groupMembers.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());

        if (expense.getType() == ExpenseType.EQUAL) {
            int memberCount = groupMembers.size();
            // Edge case: Group with 0 or 1 member?
            if (groupMembers.isEmpty()) {
                 // But splitAmount logic holds. 1 member -> 100% split. 
            }

            BigDecimal splitAmount = expense.getAmount().divide(BigDecimal.valueOf(memberCount), 2, java.math.RoundingMode.FLOOR);
            BigDecimal remainder = expense.getAmount().subtract(splitAmount.multiply(BigDecimal.valueOf(memberCount)));

            for (int i = 0; i < memberCount; i++) {
                BigDecimal allocateAmount = splitAmount;
                if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                    allocateAmount = allocateAmount.add(new BigDecimal("0.01"));
                    remainder = remainder.subtract(new BigDecimal("0.01"));
                }
                
                splits.add(ExpenseSplit.builder()
                        .expense(expense)
                        .user(groupMembers.get(i))
                        .amount(allocateAmount)
                        .build());
            }
        } else if (expense.getType() == ExpenseType.EXACT) {
            BigDecimal totalSplit = BigDecimal.ZERO;
            if (splitData == null || splitData.isEmpty()) {
                throw new IllegalArgumentException("Exact splits must be provided for type EXACT");
            }
            
            System.out.println("DEBUG: Processing EXACT split. splitData size: " + splitData.size());
            
             for (Map.Entry<Long, BigDecimal> entry : splitData.entrySet()) {
                 System.out.println("DEBUG: Split for User " + entry.getKey() + " = " + entry.getValue());
                 if (!memberIds.contains(entry.getKey())) {
                     throw new IllegalArgumentException("User ID " + entry.getKey() + " is not a member of this group");
                 }
                 User member = userRepository.findById(entry.getKey())
                         .orElseThrow(() -> new RuntimeException("Member not found for ID: " + entry.getKey()));
                 
                 splits.add(ExpenseSplit.builder()
                         .expense(expense)
                         .user(member)
                         .amount(entry.getValue())
                         .build());
                 totalSplit = totalSplit.add(entry.getValue());
             }
             System.out.println("DEBUG: Total Split Sum: " + totalSplit + ", Expected: " + expense.getAmount());
             if (totalSplit.compareTo(expense.getAmount()) != 0) {
                 throw new IllegalArgumentException("Sum of expense splits (" + totalSplit + ") does not equal total amount (" + expense.getAmount() + ")");
             }
        } else if (expense.getType() == ExpenseType.PERCENTAGE) {
            BigDecimal totalPercent = BigDecimal.ZERO;
            BigDecimal totalAllocated = BigDecimal.ZERO;
            
            // splitData Key: UserId, Value: Percentage (e.g. 50.0 for 50%)
            for (Map.Entry<Long, BigDecimal> entry : splitData.entrySet()) {
                totalPercent = totalPercent.add(entry.getValue());
            }
            
            if (totalPercent.compareTo(new BigDecimal("100.00")) != 0 && totalPercent.compareTo(new BigDecimal("100")) != 0) {
                 // Relaxed check for 100
                 throw new IllegalArgumentException("Sum of percentages (" + totalPercent + ") must equal 100");
            }

            int count = 0;
            int size = splitData.size();
            
            for (Map.Entry<Long, BigDecimal> entry : splitData.entrySet()) {
                if (!memberIds.contains(entry.getKey())) {
                     throw new IllegalArgumentException("User ID " + entry.getKey() + " is not a member of this group");
                }
                User member = userRepository.findById(entry.getKey())
                        .orElseThrow(() -> new RuntimeException("Member not found for ID: " + entry.getKey()));

                BigDecimal percentage = entry.getValue();
                BigDecimal amount;
                
                if (count == size - 1) {
                    // Last person gets the remaining amount to ensure sum is exact
                    amount = expense.getAmount().subtract(totalAllocated);
                } else {
                    amount = expense.getAmount().multiply(percentage).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    totalAllocated = totalAllocated.add(amount);
                }

                splits.add(ExpenseSplit.builder()
                        .expense(expense)
                        .user(member)
                        .amount(amount)
                        .build());
                count++;
            }
        }

        return splits;
    }
}
