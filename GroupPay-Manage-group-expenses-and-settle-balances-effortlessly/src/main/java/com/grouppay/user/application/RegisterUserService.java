package com.grouppay.user.application;

import com.grouppay.user.domain.Role;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterUserService {

    @Autowired
    UserRepository repository;

    private final PasswordEncoder encoder;

    /**
     * Registers a new user with the given email and password.
     * <p>
     * - Encodes the password using BCrypt.
     * - Sets the default role to USER.
     * - Saves the user to the database.
     * </p>
     *
     * @param email    The email of the user (must be unique).
     * @param password The raw password (will be encrypted).
     * @param username The unique username for the user.
     */
    public void register(String email, String password, String username, Integer avatarId, String currencyCode) {

        User user = User.builder()
                .email(email)
                .username(username)
                .password(encoder.encode(password))
                .role(Role.USER)
                .avatarId(avatarId != null ? avatarId : 1)
                .currencyCode(currencyCode != null ? currencyCode : "INR")
                .build();

        repository.save(user);
    }
}
