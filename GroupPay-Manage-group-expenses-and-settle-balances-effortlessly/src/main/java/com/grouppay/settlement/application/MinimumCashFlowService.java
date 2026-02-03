package com.grouppay.settlement.application;

import com.grouppay.settlement.domain.Settlement;
import com.grouppay.settlement.domain.UserBalance;
import com.grouppay.group.domain.Group;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MinimumCashFlowService {

    private final UserRepository userRepository;

    /**
     * Calculates the minimum number of transactions required to settle debts.
     * <p>
     * This uses a greedy algorithm to match the person with the maximum debt
     * to the person with the maximum credit, repeatedly, until all balances are zero.
     * </p>
     *
     * @param group       The group context.
     * @param netBalances The map of net balances (User ID -> Amount).
     * @return A list of proposed Settlement transactions.
     */
    public List<Settlement> calculateSettlements(Group group, Map<Long, BigDecimal> netBalances) {
        List<Settlement> settlements = new ArrayList<>();
        List<UserBalance> balances = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) != 0) {
                balances.add(new UserBalance(entry.getKey(), entry.getValue()));
            }
        }

        minCashFlowRec(balances, settlements, group);
        return settlements;
    }

    /**
     * Recursive helper to execute the minimum cash flow algorithm.
     * <p>
     * Logic:
     * 1. Find max creditor (positive balance) and max debtor (negative balance).
     * 2. Determine min amount of the two (abs value).
     * 3. Process transaction: Debtor pays Creditor this min amount.
     * 4. Update balances and recurse until 0.
     * </p>
     *
     * @param balances    List of mutable UserBalance objects.
     * @param settlements Accumulator list for settlement transactions.
     * @param group       The group context.
     */
    private void minCashFlowRec(List<UserBalance> balances, List<Settlement> settlements, Group group) {
        if (balances.isEmpty()) return;

        int maxCreditIndex = getMaxIndex(balances);
        int maxDebitIndex = getMinIndex(balances);

        if (balances.get(maxCreditIndex).getBalance().compareTo(BigDecimal.ZERO) == 0 &&
            balances.get(maxDebitIndex).getBalance().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal minAmount = balances.get(maxCreditIndex).getBalance()
                .min(balances.get(maxDebitIndex).getBalance().abs());

        // Debtor pays Creditor
        Long debtorId = balances.get(maxDebitIndex).getUserId();
        Long creditorId = balances.get(maxCreditIndex).getUserId();

        balances.get(maxCreditIndex).setBalance(balances.get(maxCreditIndex).getBalance().subtract(minAmount));
        balances.get(maxDebitIndex).setBalance(balances.get(maxDebitIndex).getBalance().add(minAmount));

        User payer = userRepository.findById(debtorId).orElseThrow();
        User payee = userRepository.findById(creditorId).orElseThrow();

        settlements.add(Settlement.builder()
                .group(group)
                .payer(payer)
                .payee(payee)
                .amount(minAmount)
                .isSettled(false)
                .build());

        minCashFlowRec(balances, settlements, group);
    }

    private int getMaxIndex(List<UserBalance> balances) {
        int maxInd = 0;
        for (int i = 1; i < balances.size(); i++) {
            if (balances.get(i).getBalance().compareTo(balances.get(maxInd).getBalance()) > 0) {
                maxInd = i;
            }
        }
        return maxInd;
    }

    private int getMinIndex(List<UserBalance> balances) {
        int minInd = 0;
        for (int i = 1; i < balances.size(); i++) {
            if (balances.get(i).getBalance().compareTo(balances.get(minInd).getBalance()) < 0) {
                minInd = i;
            }
        }
        return minInd;
    }
}
