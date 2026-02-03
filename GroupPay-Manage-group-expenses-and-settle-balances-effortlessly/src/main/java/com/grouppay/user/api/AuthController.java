package com.grouppay.user.api;

import com.grouppay.user.application.LoginUserService;
import com.grouppay.user.application.PasswordResetService;
import com.grouppay.user.application.RegisterUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    @Autowired
    LoginUserService loginuserService;
    @Autowired
    RegisterUserService registerUserService;
    @Autowired
    com.grouppay.user.infrastructure.UserRepository userRepository;

    /**
     * Registers a new user.
     * 
     * @param email    User's email.
     * @param password User's password.
     * @param username User's username.
     * @return Success message.
     */
    @PostMapping("/register")
    public String register(
            @RequestParam String email, 
            @RequestParam String password, 
            @RequestParam String username,
            @RequestParam(required = false) Integer avatarId,
            @RequestParam(required = false) String currencyCode) {
        registerUserService.register(email, password, username, avatarId, currencyCode);

        return "User registered";
    }

    /**
     * Authenticates a user and returns a JWT token with user details.
     * 
     * @param email    User's email.
     * @param password User's password.
     * @return AuthResponse containing token and userId.
     */
    @Autowired
    PasswordResetService passwordResetService;

    // ... existing login method ...
    @PostMapping("/login")
    public com.grouppay.shared.dto.AuthResponse login(@RequestParam String email, @RequestParam String password){
        String token = loginuserService.login(email, password);
        com.grouppay.user.domain.User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return com.grouppay.shared.dto.AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email) {
        // Returns token for development convenience
        return passwordResetService.initiatePasswordReset(email);
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        passwordResetService.resetPassword(token, newPassword);
        return "Password reset successfully";
    }
}
