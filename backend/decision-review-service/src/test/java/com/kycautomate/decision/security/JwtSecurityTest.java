package com.kycautomate.decision.security;

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
import com.kycautomate.decision.controller.ReviewerAuthController;
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
    "security.jwt.issuer=kyc-decision",
    "security.jwt.audience=kyc-decision-api",
    "security.jwt.ttl-seconds=900",
    "review.auth.username=reviewer", "review.auth.password=TestPassword@123"
})
class JwtSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired JwtCodec codec;
    @Autowired JwtTokenService tokens;
    
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        
    }

    private String signed(String issuer,String audience,String role,Instant expiry) {
        var key=new SecretKeySpec(Base64.getDecoder().decode("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="),"HmacSHA256");
        var claims=Jwts.builder().issuer(issuer).subject("reviewer").audience().add(audience).and()
            .issuedAt(Date.from(Instant.now().minusSeconds(600))).claim("roles",List.of(role));
        if(expiry!=null) claims.expiration(Date.from(expiry));
        return claims.signWith(key,Jwts.SIG.HS256).compact();
    }

    private String validToken() {
        return tokens.issue("reviewer").value();
    }

    @Test void loginReturnsBearerToken() throws Exception {
        mvc.perform(post("/api/reviewer-auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reviewer\",\"password\":\"TestPassword@123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void wrongLoginPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/reviewer-auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reviewer\",\"password\":\"WrongPassword\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void validBearerTokenPreservesPrincipalName() throws Exception {
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + validToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actor").value("reviewer"));
    }

    @Test void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/reviews/pending")).andExpect(status().isUnauthorized());
    }

    @Test void publicApiDoesNotAcceptBasicAuth() throws Exception {
        mvc.perform(get("/api/reviews/pending").with(httpBasic("reviewer", "TestPassword@123")))
            .andExpect(status().isUnauthorized());
    }

    @Test void badSignatureIsRejected() throws Exception {
        String[] parts = validToken().split("\\.");
        String badToken = parts[0] + "." + parts[1] + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + badToken))
            .andExpect(status().isUnauthorized());
    }

    @Test void expiredTokenIsRejected() throws Exception {
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + signed(
                "kyc-decision", "kyc-decision-api", "REVIEWER", Instant.now().minusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void wrongIssuerIsRejected() throws Exception {
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + signed(
                "another-issuer", "kyc-decision-api", "REVIEWER", Instant.now().plusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void wrongAudienceIsRejected() throws Exception {
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + signed(
                "kyc-decision", "another-api", "REVIEWER", Instant.now().plusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void wrongRoleIsRejected() throws Exception {
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + signed(
                "kyc-decision", "kyc-decision-api", "ADMIN", Instant.now().plusSeconds(300))))
            .andExpect(status().isUnauthorized());
    }

    @Test void tokenWithoutExpiryIsRejected() throws Exception {
        mvc.perform(get("/api/reviews/pending").header("Authorization", "Bearer " + signed(
                "kyc-decision", "kyc-decision-api", "REVIEWER", null)))
            .andExpect(status().isUnauthorized());
    }

    

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtConfig.class, JwtTokenService.class,
        ProtectedController.class, ReviewerAuthController.class})
    static class TestConfiguration {
        
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/api/reviews/pending")
        Map<String, String> protectedEndpoint(Authentication auth) {
            return Map.of("actor", auth.getName());
        }
        
    }
}
