package com.kycautomate.customer.security;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.kycautomate.customer.enums.AccountStatus;
import com.kycautomate.customer.repository.AppUserRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager internalUsers(
            PasswordEncoder encoder,
            @Value("${internal.auth.verification-password}")
            String verificationPassword,
            @Value("${internal.auth.decision-password}")
            String decisionPassword) {

        return new InMemoryUserDetailsManager(
            User.withUsername("verification-service")
                .password(encoder.encode(verificationPassword))
                .roles("VERIFICATION_SERVICE")
                .build(),

            User.withUsername("decision-service")
                .password(encoder.encode(decisionPassword))
                .roles("DECISION_SERVICE")
                .build()
        );
    }

    @Bean
    public UserDetailsService customerUsers(
            AppUserRepository repository) {

        return email -> {
            String normalized = email.trim()
                .toLowerCase(Locale.ROOT);

            var user = repository.findByEmail(normalized)
                .orElseThrow(() ->
                    new UsernameNotFoundException(
                        "Invalid credentials"));

            return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(
                    user.getAccountStatus() != AccountStatus.ACTIVE)
                .build();
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurity(
            HttpSecurity http,
            InMemoryUserDetailsManager internalUsers)
            throws Exception {

        http
            .securityMatcher("/api/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .authenticationProvider(
                internalAuthenticationProvider(internalUsers))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/internal/kyc/applications/*/snapshot"
                ).hasAnyRole(
                    "VERIFICATION_SERVICE",
                    "DECISION_SERVICE"
                )
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/internal/kyc/applications/*/status"
                ).hasRole("DECISION_SERVICE")
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/internal/kyc/applications/*/status"
                ).hasRole("DECISION_SERVICE")
                .anyRequest().denyAll()
            )
            .httpBasic(basic -> {});

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurity(
            HttpSecurity http,
            UserDetailsService customerUsers)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .authenticationProvider(
                customerAuthenticationProvider(customerUsers))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST, "/api/auth/register"
                ).permitAll()
                .requestMatchers("/api/customers/**")
                    .hasRole("CUSTOMER")
                .requestMatchers("/api/kyc/**")
                    .hasRole("CUSTOMER")
                .anyRequest().denyAll()
            )
            .httpBasic(basic -> {});

        return http.build();
    }

    private org.springframework.security.authentication
            .dao.DaoAuthenticationProvider
            internalAuthenticationProvider(
                    UserDetailsService users) {

        var provider =
            new org.springframework.security.authentication
                .dao.DaoAuthenticationProvider(users);

        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    private org.springframework.security.authentication
            .dao.DaoAuthenticationProvider
            customerAuthenticationProvider(
                    UserDetailsService users) {

        var provider =
            new org.springframework.security.authentication
                .dao.DaoAuthenticationProvider(users);

        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}