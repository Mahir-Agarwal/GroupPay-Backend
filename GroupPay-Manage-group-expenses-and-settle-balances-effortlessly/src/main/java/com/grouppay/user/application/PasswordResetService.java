package com.grouppay.user.application;

import com.grouppay.user.domain.PasswordResetToken;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.PasswordResetTokenRepository;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String initiatePasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = UUID.randomUUID().toString();
        // Remove any existing token for this user
        // Note: Ideally we'd have a clean-up job or logic, but for now we generate a new one.
        // If we want to strictly enforce one valid token, we should find and delete/invalidate old ones.
        // Assuming database constraints or just letting multiple exist (if logic allows) is fine for MVP.
        // But let's keep it simple: just create a new one.

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(15)) // 15 min expiry
                .build();

        tokenRepository.save(resetToken);

        // MOCK EMAIL SENDING
        System.out.println("==========================================");
        System.out.println("PASSWORD RESET TOKEN FOR " + email + ": " + token);
        System.out.println("==========================================");
        
        return token;
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(resetToken);
    }
}
