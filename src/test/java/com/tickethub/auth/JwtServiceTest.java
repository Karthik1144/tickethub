package com.tickethub.auth;

import com.tickethub.auth.security.JwtService;
import com.tickethub.config.TicketHubProperties;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit test: no Spring context, no database. */
class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        TicketHubProperties props = new TicketHubProperties();
        props.getJwt().setSecret("unit-test-secret-key-long-enough-for-hs256-algorithm");
        props.getJwt().setIssuer("tickethub-unit");
        jwtService = new JwtService(props);

        user = new User("asha@example.com", "hash", "Asha Rao", null, Role.USER);
        setId(user, 42L);
    }

    @Test
    @DisplayName("a generated token carries the user id, role and issuer")
    void tokenCarriesClaims() {
        Claims claims = jwtService.parse(jwtService.generateAccessToken(user));

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("email", String.class)).isEqualTo("asha@example.com");
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void tamperedTokenIsRejected() {
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtService.parse(tampered)).isNull();
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void foreignTokenIsRejected() {
        TicketHubProperties other = new TicketHubProperties();
        other.getJwt().setSecret("a-completely-different-secret-key-for-hs256-tests");
        other.getJwt().setIssuer("tickethub-unit");

        String foreign = new JwtService(other).generateAccessToken(user);

        assertThat(jwtService.parse(foreign)).isNull();
    }

    @Test
    @DisplayName("garbage input never throws, it just fails to parse")
    void garbageIsRejected() {
        assertThat(jwtService.parse("not-a-token")).isNull();
        assertThat(jwtService.parse("")).isNull();
    }

    private void setId(User target, Long id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
