package com.kycautomate.customer.security;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
            @Value("${internal.auth.verification-password}") String verificationPassword,
            @Value("${internal.auth.decision-password}") String decisionPassword) {
        return new InMemoryUserDetailsManager(
            User.withUsername("verification-service").password(encoder.encode(verificationPassword))
                .roles("VERIFICATION_SERVICE").build(),
            User.withUsername("decision-service").password(encoder.encode(decisionPassword))
                .roles("DECISION_SERVICE").build());
    }

    @Bean
    public UserDetailsService customerUsers(AppUserRepository repository) {
        return email -> {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            var user = repository.findByEmail(normalized).orElseThrow(() ->
                new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(user.getEmail()).password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(user.getAccountStatus() != AccountStatus.ACTIVE).build();
        };
    }

    @Bean("customerLoginAuthenticationManager")
    public AuthenticationManager customerLoginAuthenticationManager(
            @Qualifier("customerUsers") UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurity(
            HttpSecurity http, InMemoryUserDetailsManager internalUsers,
            PasswordEncoder encoder) throws Exception {
        var provider = new DaoAuthenticationProvider(internalUsers);
        provider.setPasswordEncoder(encoder);
        http.securityMatcher("/api/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationManager(new ProviderManager(provider))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/internal/kyc/applications/*/snapshot")
                    .hasAnyRole("VERIFICATION_SERVICE", "DECISION_SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/internal/kyc/applications/*/status")
                    .hasRole("DECISION_SERVICE")
                .requestMatchers(HttpMethod.PATCH, "/api/internal/kyc/applications/*/status")
                    .hasRole("DECISION_SERVICE")
                .anyRequest().denyAll())
            .httpBasic(basic -> {});
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurity(
            HttpSecurity http, @Qualifier("customerUsers") UserDetailsService users, JwtCodec codec) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/api/customers/**", "/api/kyc/**").hasRole("CUSTOMER")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,ex) -> { response.setStatus(401); response.setHeader("WWW-Authenticate","Bearer"); }))
            .addFilterBefore(new JwtAuthenticationFilter(codec,users),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
