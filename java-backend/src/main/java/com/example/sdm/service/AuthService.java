package com.example.sdm.service;

import com.example.sdm.crypto.JwtProvider;
import com.example.sdm.dto.LoginRequest;
import com.example.sdm.dto.SignupRequest;
import com.example.sdm.dto.TokenResponse;
import com.example.sdm.entity.User;
import com.example.sdm.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final AiServiceClient aiServiceClient;

    public AuthService(UserRepository userRepository, JwtProvider jwtProvider, PasswordEncoder passwordEncoder, AiServiceClient aiServiceClient) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.aiServiceClient = aiServiceClient;
    }

    @Transactional
    public User signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email '%s' is already registered.".formatted(request.email()));
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username '%s' is already taken.".formatted(request.username()));
        }

        // TODO: 추후 AI 서버 배포 후 연결 예정
        String walletAddress = aiServiceClient.generateWallet(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(
                request.email(),
                encodedPassword,
                request.username(),
                walletAddress,
                request.role()
        );

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .or(() -> userRepository.findByUsername(request.email())) // Allow login by username as well
                .orElseThrow(() -> new IllegalArgumentException("Invalid email/username or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email/username or password.");
        }

        String token = jwtProvider.createToken(user.getEmail());
        return new TokenResponse(token, jwtProvider.getValidityInSeconds());
    }
}
