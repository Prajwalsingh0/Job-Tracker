package com.jobhunt.service;

import com.jobhunt.dto.AuthResponse;
import com.jobhunt.dto.AuthResult;
import com.jobhunt.dto.LoginRequest;
import com.jobhunt.dto.RegisterRequest;
import com.jobhunt.dto.UserDto;
import com.jobhunt.entity.User;
import com.jobhunt.exception.ConflictException;
import com.jobhunt.repository.UserRepository;
import com.jobhunt.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()));

        return issueTokens(userRepository.save(user));
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a new access token, rotating the refresh token
     * at the same time.
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new BadCredentialsException("Refresh token is no longer valid"));

        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getName());
        return new AuthResult(
                AuthResponse.of(accessToken, toDto(user)),
                rotation.refreshToken(),
                refreshTokenService.getRefreshTtlMs());
    }

    /** Logout: revokes every refresh token held by the owner of the supplied token. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserDto currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Authenticated user no longer exists"));
        return toDto(user);
    }

    private AuthResult issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getName());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResult(
                AuthResponse.of(accessToken, toDto(user)),
                refreshToken,
                refreshTokenService.getRefreshTtlMs());
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail());
    }
}
