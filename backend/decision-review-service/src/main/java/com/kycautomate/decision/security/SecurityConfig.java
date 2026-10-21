
package com.kycautomate.decision.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService reviewerUsers(
            PasswordEncoder encoder,
            @Value("${review.auth.username}") String username,
            @Value("${review.auth.password}") String password) {

        return new InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(encoder.encode(password))
                .roles("REVIEWER")
                .build()
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.GET, "/actuator/health"
                ).permitAll()
                .requestMatchers("/api/decisions/**")
                    .hasRole("REVIEWER")
                .requestMatchers("/api/reviews/**")
                    .hasRole("REVIEWER")
                .anyRequest().denyAll()
            )
            .httpBasic(basic -> {});

        return http.build();
    }
}
