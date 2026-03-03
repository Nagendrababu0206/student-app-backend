package com.eduai.backend.service;

import com.eduai.backend.entity.UserEntity;
import com.eduai.backend.model.LoginRequest;
import com.eduai.backend.model.RegisterRequest;
import com.eduai.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    @Transactional
    public void ensureDefaultUser() {
        String defaultEmail = "student@eduai.com";
        if (userRepository.existsByEmail(defaultEmail)) {
            return;
        }

        UserEntity user = new UserEntity();
        user.setName("Student");
        user.setPhone("9999999999");
        user.setEmail(defaultEmail);
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        userRepository.save(user);
    }

    @Transactional
    public boolean register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return false;
        }

        UserEntity user = new UserEntity();
        user.setName(request.name().trim());
        user.setPhone(request.phone().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean login(LoginRequest request) {
        String email = request.username().trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .map(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .orElse(false);
    }
}
