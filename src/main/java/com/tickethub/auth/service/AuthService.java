package com.tickethub.auth.service;

import com.tickethub.auth.domain.RefreshToken;
import com.tickethub.auth.dto.AuthDtos.*;
import com.tickethub.auth.repository.RefreshTokenRepository;
import com.tickethub.auth.security.JwtService;
import com.tickethub.common.exception.ApiException;
import com.tickethub.common.exception.ErrorCode;
import com.tickethub.config.TicketHubProperties;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TicketHubProperties props;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TicketHubProperties props) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.props = props;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.phone(), Role.USER);
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect"));

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
        }
        return issueTokens(user);
    }

    /** Rotates the refresh token: the presented token is revoked and a new one is issued. */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token is not valid"));

        if (!stored.isUsable(Instant.now())) {
            // A revoked token being replayed means the family may be compromised.
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId());
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token is not valid");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        return toResponse(userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User")));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        byte[] raw = new byte[48];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        Instant expiresAt = Instant.now().plus(Duration.ofDays(props.getJwt().getRefreshTokenDays()));
        refreshTokenRepository.save(new RefreshToken(user, sha256(refreshToken), expiresAt));

        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenSeconds());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getRole().name());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
