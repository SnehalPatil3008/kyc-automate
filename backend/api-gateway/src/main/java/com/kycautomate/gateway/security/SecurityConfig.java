package com.kycautomate.gateway.security;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience) {

        byte[] decodedSecret = Base64.getDecoder().decode(secret);

        SecretKey key = new SecretKeySpec(
                decodedSecret,
                "HmacSHA256"
        );

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder
                        .withSecretKey(key)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuer);

        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(
                        "aud",
                        values -> values != null
                                && values.contains(audience)
                );

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerValidator,
                        audienceValidator
                )
        );

        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange

                        .pathMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        .pathMatchers(
                                "/api/auth/**",
                                "/api/reviewer-auth/**",
                                "/actuator/**"
                        )
                        .permitAll()

                        .pathMatchers(
                                "/api/customers/**",
                                "/api/kyc/**",
                                "/api/documents/**"
                        )
                        .hasRole("CUSTOMER")

                        .pathMatchers(
                                "/api/reviews/**",
                                "/api/decisions/**"
                        )
                        .hasAnyRole("CUSTOMER", "REVIEWER")

                        .pathMatchers("/api/audit/**")
                        .hasRole("ADMIN")

                        .pathMatchers("/api/verifications/**")
                        .hasAnyRole("CUSTOMER", "REVIEWER", "ADMIN")

                        .anyExchange()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                )
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>>
            jwtAuthenticationConverter() {

        return jwt -> {
            List<String> roles =
                    jwt.getClaimAsStringList("roles");

            if (roles == null) {
                roles = List.of();
            }

            List<SimpleGrantedAuthority> authorities =
                    roles.stream()
                            .map(role ->
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + role
                                    )
                            )
                            .toList();

            return Mono.just(
                    new JwtAuthenticationToken(
                            jwt,
                            authorities
                    )
            );
        };
    }
}