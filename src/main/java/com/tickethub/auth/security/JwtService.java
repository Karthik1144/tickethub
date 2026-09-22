package com.tickethub.auth.security;

import com.tickethub.config.TicketHubProperties;
import com.tickethub.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final TicketHubProperties props;

    public JwtService(TicketHubProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(props.getJwt().getAccessTokenMinutes()));
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(props.getJwt().getIssuer())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long accessTokenSeconds() {
        return Duration.ofMinutes(props.getJwt().getAccessTokenMinutes()).toSeconds();
    }

    /** Returns the parsed claims, or null when the token is missing, expired or tampered with. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
