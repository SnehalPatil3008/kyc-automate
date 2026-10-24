package com.kycautomate.decision.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.core.annotation.Order;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
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
        return new InMemoryUserDetailsManager(User.withUsername(username)
            .password(encoder.encode(password)).roles("REVIEWER").build());
    }
    
    @Bean("internalVerificationUsers")
    public InMemoryUserDetailsManager internalVerificationUsers(
            PasswordEncoder encoder,
            @Value("${internal.verification.password}")
            String password) {

        return new InMemoryUserDetailsManager(
            User.withUsername("verification-service")
                .password(encoder.encode(password))
                .roles("VERIFICATION_SERVICE")
                .build()
        );
    }
    
    @Bean("reviewerLoginAuthenticationManager")
    public AuthenticationManager reviewerLoginAuthenticationManager(
            @Qualifier("reviewerUsers") UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("internalVerificationUsers")
            InMemoryUserDetailsManager users,
            PasswordEncoder encoder) throws Exception {

        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);

        http
            .securityMatcher("/api/internal/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .authenticationManager(
                new ProviderManager(provider)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/internal/decisions/applications/*/evaluate"
                )
                .hasRole("VERIFICATION_SERVICE")
                .anyRequest()
                .denyAll()
            )
            .httpBasic(basic -> {});

        return http.build();
    }
    
    
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("reviewerUsers") UserDetailsService users, JwtCodec codec) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/reviewer-auth/login").permitAll()
                .requestMatchers("/api/decisions/**", "/api/reviews/**").hasRole("REVIEWER")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,ex) -> { response.setStatus(401); response.setHeader("WWW-Authenticate","Bearer"); }))
            .addFilterBefore(new JwtAuthenticationFilter(codec,users),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
