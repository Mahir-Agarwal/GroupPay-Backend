package com.grouppay.security;

import com.grouppay.user.infrastructure.UserRepository;
import jakarta.servlet.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter filter;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/oauth2/**", "/error" ,"/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String email = oAuth2User.getAttribute("email");
            
            // Check if user exists, else register
            userRepository.findByEmail(email).orElseGet(() -> {
                com.grouppay.user.domain.User newUser = com.grouppay.user.domain.User.builder()
                        .email(email)
                        .username(email.split("@")[0]) // Default username from email
                        .password(passwordEncoder().encode("OAUTH2_USER_" + java.util.UUID.randomUUID()))
                        .role(com.grouppay.user.domain.Role.USER) 
                        .build();
                return userRepository.save(newUser);
            });

            // Generate JWT
            String token = jwtUtil.generateToken(email);

            // Redirect to frontend with token
            response.setContentType("application/json");
            response.getWriter().write("{\"token\": \"" + token + "\", \"message\": \"Login Successful. Use this token for future requests.\"}");
            response.getWriter().flush();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
