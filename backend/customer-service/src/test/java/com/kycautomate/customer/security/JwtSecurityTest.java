package com.kycautomate.customer.security;

import java.time.Instant;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Jwts;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Optional;
import com.kycautomate.customer.controller.AuthController;
import com.kycautomate.customer.service.AuthService;
import com.kycautomate.customer.entity.AppUser;
import com.kycautomate.customer.enums.AccountStatus;
import com.kycautomate.customer.enums.UserRole;
import com.kycautomate.customer.repository.AppUserRepository;
import com.kycautomate.customer.repository.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = JwtSecurityTest.TestConfiguration.class)
@TestPropertySource(properties = {
    "security.jwt.secret=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
    "security.jwt.issuer=kyc-customer",
    "security.jwt.audience=kyc-customer-api",
    "security.jwt.ttl-seconds=900",
    "internal.auth.verification-password=test-verification-secret",
    "internal.auth.decision-password=test-decision-secret"
})
class JwtSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired JwtCodec codec;
    @Autowired JwtTokenService tokens;
    @Autowired AppUserRepository repository;
    @Autowired PasswordEncoder passwordEncoder;
    AppUser user;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        user = new AppUser();
        user.setUserId(1L);
        user.setEmail("john.test@example.com");
        user.setPasswordHash(passwordEncoder.encode("TestPassword@123"));
        user.setRole(UserRole.CUSTOMER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        when(repository.findByEmail("john.test@example.com")).thenReturn(Optional.of(user));
    }

    private String signed(String issuer,String audience,String role,Instant expiry) {
        var key=new SecretKeySpec(Base64.getDecoder().decode("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="),"HmacSHA256");
        var claims=Jwts.builder().issuer(issuer).subject("john.test@example.com").audience().add(audience).and()
            .issuedAt(Date.from(Instant.now().minusSeconds(600))).claim("roles",List.of(role));
        if(expiry!=null) claims.expiration(Date.from(expiry));
        return claims.signWith(key,Jwts.SIG.HS256).compact();
    }

    private String validToken() {
        return tokens.issue("john.test@example.com").value();
    }

    @Test void loginReturnsBearerToken() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john.test@example.com\",\"password\":\"TestPassword@123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void wrongLoginPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"john.test@example.com\",\"password\":\"WrongPassword\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void validBearerTokenPreservesPrincipalName() throws Exception {
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + validToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actor").value("john.test@example.com"));
    }

    @Test void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/customers/me")).andExpect(status().isUnauthorized());
    }

    @Test void publicApiDoesNotAcceptBasicAuth() throws Exception {
        mvc.perform(get("/api/customers/me").with(httpBasic("john.test@example.com", "TestPassword@123")))
            .andExpect(status().isUnauthorized());
    }

    @Test void badSignatureIsRejected() throws Exception {
        String[] parts = validToken().split("\\.");
        String badToken = parts[0] + "." + parts[1] + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + badToken))
            .andExpect(status().isUnauthorized());
    }

    @Test void expiredTokenIsRejected() throws Exception {
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + signed(
                "kyc-customer", "kyc-customer-api", "CUSTOMER", Instant.now().minusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void wrongIssuerIsRejected() throws Exception {
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + signed(
                "another-issuer", "kyc-customer-api", "CUSTOMER", Instant.now().plusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void wrongAudienceIsRejected() throws Exception {
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + signed(
                "kyc-customer", "another-api", "CUSTOMER", Instant.now().plusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void wrongRoleIsRejected() throws Exception {
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + signed(
                "kyc-customer", "kyc-customer-api", "ADMIN", Instant.now().plusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void tokenWithoutExpiryIsRejected() throws Exception {
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + signed(
                "kyc-customer", "kyc-customer-api", "CUSTOMER", null)))
            .andExpect(status().isUnauthorized());
    }

    @Test void internalDecisionBasicAuthStillWorks() throws Exception {
        mvc.perform(post("/api/internal/kyc/applications/41/status")
                .with(httpBasic("decision-service", "test-decision-secret")))
            .andExpect(status().isOk());
    }

    @Test void internalVerificationSnapshotStillWorks() throws Exception {
        mvc.perform(get("/api/internal/kyc/applications/41/snapshot")
                .with(httpBasic("verification-service", "test-verification-secret")))
            .andExpect(status().isOk());
    }

    @Test void verificationAccountCannotChangeStatus() throws Exception {
        mvc.perform(post("/api/internal/kyc/applications/41/status")
                .with(httpBasic("verification-service", "test-verification-secret")))
            .andExpect(status().isForbidden());
    }

    @Test void customerTokenCannotCallInternalSnapshot() throws Exception {
        mvc.perform(get("/api/internal/kyc/applications/41/snapshot")
                .header("Authorization", "Bearer " + validToken()))
            .andExpect(status().isUnauthorized());
    }

    @Test void disabledCustomerCannotContinueUsingToken() throws Exception {
        String token = validToken();
        user.setAccountStatus(AccountStatus.DISABLED);
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, JwtTokenService.class,
        ProtectedController.class, AuthController.class, AuthService.class})
    static class TestConfiguration {
        @Bean AppUserRepository repository() { return mock(AppUserRepository.class); }
        @Bean CustomerRepository customerRepository() { return mock(CustomerRepository.class); }
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/api/customers/me")
        Map<String, String> protectedEndpoint(Authentication auth) {
            return Map.of("actor", auth.getName());
        }
        @GetMapping("/api/internal/kyc/applications/41/snapshot")
        Map<String, String> snapshot() { return Map.of("applicationId", "41"); }
        @PostMapping("/api/internal/kyc/applications/41/status")
        Map<String, String> update() { return Map.of("status", "PROCESSING"); }
    }
}
