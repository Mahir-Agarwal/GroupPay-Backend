package com.grouppay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@lombok.extern.slf4j.Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
     JwtUtil jwtUtil;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();


        if (path.startsWith("/auth")) {
            filterChain.doFilter(request, response);
            return;
        }
            String authHeader = request.getHeader("Authorization");

            if(authHeader !=null && authHeader.startsWith("Bearer ")){
                String token = authHeader.substring(7);
                log.info("JWT Filter: Token found, length={}", token.length());
                try {
                    String email = jwtUtil.extractEmail(token);
                    log.info("JWT Filter: Extracted email={}", email);
                    UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(email,null, java.util.Collections.emptyList());
    
                    authenticationToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
    
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } catch (Exception e) {
                    log.error("JWT Filter: Token extraction failed: {}", e.getMessage());
                }
            } else {
                log.warn("JWT Filter: No Bearer token found in header for path: {}", path);
            }

            filterChain.doFilter(request,response);
    }
}



