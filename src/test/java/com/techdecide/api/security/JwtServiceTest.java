package com.techdecide.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "test-secret-key-for-testing-purposes-only-min-32chars";
    private static final long EXPIRATION = 86400000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", EXPIRATION);
    }

    private UserDetails buildUser(String email) {
        return User.builder()
                .username(email)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
    }

    @Test
    void generateToken_validUserDetails_returnsNonNullToken() {
        UserDetails userDetails = buildUser("test@example.com");

        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_tokenContainsThreeParts() {
        UserDetails userDetails = buildUser("test@example.com");

        String token = jwtService.generateToken(userDetails);

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractEmail_fromGeneratedToken_returnsCorrectEmail() {
        String email = "user@example.com";
        UserDetails userDetails = buildUser(email);
        String token = jwtService.generateToken(userDetails);

        String extracted = jwtService.extractEmail(token);

        assertThat(extracted).isEqualTo(email);
    }

    @Test
    void isTokenValid_validTokenMatchingUser_returnsTrue() {
        UserDetails userDetails = buildUser("valid@example.com");
        String token = jwtService.generateToken(userDetails);

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    void isTokenValid_SEC_08_statelessLogoutPolicy_tokenRemainsValidUntilExpiry() {
        UserDetails userDetails = buildUser("logout-policy@example.com");
        String token = jwtService.generateToken(userDetails);

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    void isTokenValid_validTokenWrongUser_returnsFalse() {
        UserDetails owner = buildUser("owner@example.com");
        UserDetails other = buildUser("other@example.com");
        String token = jwtService.generateToken(owner);

        boolean valid = jwtService.isTokenValid(token, other);

        assertThat(valid).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1000L);
        UserDetails userDetails = buildUser("expired@example.com");
        String token = jwtService.generateToken(userDetails);
        ReflectionTestUtils.setField(jwtService, "expiration", EXPIRATION);

        assertThrows(Exception.class, () -> jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void extractEmail_invalidToken_throwsException() {
        assertThrows(Exception.class, () -> jwtService.extractEmail("not.a.valid.token"));
    }

    @Test
    void generateToken_differentUsers_produceDifferentTokens() {
        UserDetails user1 = buildUser("a@example.com");
        UserDetails user2 = buildUser("b@example.com");

        String token1 = jwtService.generateToken(user1);
        String token2 = jwtService.generateToken(user2);

        assertThat(token1).isNotEqualTo(token2);
    }
}
