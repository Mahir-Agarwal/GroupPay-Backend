package com.grouppay.user.application;


import com.grouppay.security.JwtUtil;
import com.grouppay.user.domain.User;
import com.grouppay.user.infrastructure.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginUserService {

    @Autowired
    UserRepository repository;
    @Autowired
     JwtUtil jwtUtil;

    @Autowired
      PasswordEncoder encoder ;

    public String login(String email,String password){

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));


        if(!encoder.matches(password, user.getPassword())){
            throw new RuntimeException("Invalid Credentials");
        }

        return jwtUtil.generateToken(user.getEmail());
    }
}
