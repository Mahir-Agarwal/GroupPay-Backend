package com.grouppay.settlement.api;

import com.grouppay.group.domain.Group;
import com.grouppay.group.infrastructure.GroupRepository;
import com.grouppay.settlement.application.BalanceCalculationService;
import com.grouppay.settlement.application.MinimumCashFlowService;
import com.grouppay.settlement.domain.Settlement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final BalanceCalculationService balanceCalculationService;
    private final MinimumCashFlowService minimumCashFlowService;
    private final GroupRepository groupRepository;
    private final com.grouppay.notification.application.NotificationService notificationService;
    private final com.grouppay.user.infrastructure.UserRepository userRepository;


    /**
     * Calculates and returns the optimized transactions required to settle all debts in a group.
     * 
     * @param groupId ID of the group.
     * @return List of proposed settlements.
     */
    @GetMapping("/group/{groupId}/calculate")
    public ResponseEntity<List<Settlement>> getOptimizedSettlements(@PathVariable Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        Map<Long, BigDecimal> netBalances = balanceCalculationService.calculateNetBalances(groupId);
        List<Settlement> settlements = minimumCashFlowService.calculateSettlements(group, netBalances);

        return ResponseEntity.ok(settlements);
    }

    /**
     * Sends a payment reminder to a specific user.
     */
    @PostMapping("/{memberId}/reminder")
    public ResponseEntity<Void> sendReminder(@PathVariable Long memberId, org.springframework.security.core.Authentication authentication) {
        String email = authentication.getName();
        com.grouppay.user.domain.User sender = userRepository.findByEmail(email).orElseThrow();
        
        notificationService.createNotification(
                memberId,
                "Payment Reminder",
                sender.getUsername() + " has sent you a payment reminder.",
                com.grouppay.notification.domain.NotificationType.REMINDER
        );
        return ResponseEntity.ok().build();
    }
}
