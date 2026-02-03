package com.grouppay.user.api;

import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final com.grouppay.group.infrastructure.GroupRepository groupRepository;
    private final com.grouppay.settlement.application.BalanceCalculationService balanceCalculationService;

    /**
     * Get the current user's profile summary and global balances.
     */
    @GetMapping("/me/summary")
    public ResponseEntity<UserSummaryDto> getMySummary(org.springframework.security.core.Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        
        // Calculate balances across ALL groups
        java.util.List<com.grouppay.group.domain.Group> userGroups = groupRepository.findByMembers_User_Id(user.getId());
        java.math.BigDecimal totalYouOwe = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalOwedToYou = java.math.BigDecimal.ZERO;

        for (com.grouppay.group.domain.Group group : userGroups) {
            java.util.Map<Long, java.math.BigDecimal> groupBalances = balanceCalculationService.calculateNetBalances(group.getId());
            java.math.BigDecimal userBalance = groupBalances.getOrDefault(user.getId(), java.math.BigDecimal.ZERO);
            
            if (userBalance.compareTo(java.math.BigDecimal.ZERO) > 0) {
                totalOwedToYou = totalOwedToYou.add(userBalance);
            } else {
                totalYouOwe = totalYouOwe.add(userBalance.abs());
            }
        }

        return ResponseEntity.ok(UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .avatarId(user.getAvatarId())
                .currencyCode(user.getCurrencyCode())
                .totalYouOwe(totalYouOwe)
                .totalOwedToYou(totalOwedToYou)
                .netBalance(totalOwedToYou.subtract(totalYouOwe))
                .build());
    }

    /**
     * Update user profile preferences (Avatar and Currency).
     */
    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(
            @RequestParam(required = false) Integer avatarId,
            @RequestParam(required = false) String currencyCode,
            org.springframework.security.core.Authentication auth) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        
        if (avatarId != null) user.setAvatarId(avatarId);
        if (currencyCode != null) user.setCurrencyCode(currencyCode);
        
        return ResponseEntity.ok(userRepository.save(user));
    }

    /**
     * Search for a user by email or username.
     * 
     * @param email    Optional user's email.
     * @param username Optional user's username.
     * @return User object (without password) or 404.
     */
    @GetMapping("/search")
    public ResponseEntity<User> searchUser(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username) {
        if (username != null) {
            return userRepository.findByUsername(username)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        if (email != null) {
            return userRepository.findByEmail(email)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.badRequest().build();
    }
}
