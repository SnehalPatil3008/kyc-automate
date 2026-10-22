package com.kycautomate.document.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtCodec codec) throws Exception {

        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(codec, null);

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/documents/**")
                        .hasAnyRole("CUSTOMER", "REVIEWER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/documents/**")
                        .hasAnyRole("CUSTOMER", "REVIEWER")

                        .anyRequest().denyAll())
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}