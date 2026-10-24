package com.kycautomate.audit.security;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService adminUsers(
            PasswordEncoder passwordEncoder,
            @Value("${audit.auth.username}")
            String username,
            @Value("${audit.auth.password}")
            String password) {

        return new InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(
                    passwordEncoder.encode(password)
                )
                .roles("ADMIN")
                .build()
        );
    }

    @Bean("adminLoginAuthenticationManager")
    public AuthenticationManager
            adminLoginAuthenticationManager(

            @Qualifier("adminUsers")
            UserDetailsService adminUsers,

            PasswordEncoder passwordEncoder) {

        var provider =
            new DaoAuthenticationProvider(adminUsers);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtCodec jwtCodec) throws Exception {

        JwtAuthenticationFilter jwtFilter =
            new JwtAuthenticationFilter(jwtCodec);

        return http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(authorize -> authorize

                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/admin-auth/login"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/audit/events"
                ).hasRole("SERVICE")

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/audit/applications/**"
                ).hasAnyRole(
                    "CUSTOMER",
                    "REVIEWER",
                    "ADMIN"
                )

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/audit/reports/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/audit/events"
                ).hasRole("ADMIN")

                .anyRequest().denyAll()
            )

            .exceptionHandling(exceptions -> exceptions

                .authenticationEntryPoint(
                    (request, response, exception) -> {
                        response.setStatus(401);
                        response.setHeader(
                            "WWW-Authenticate",
                            "Bearer"
                        );
                    }
                )

                .accessDeniedHandler(
                    (request, response, exception) ->
                        response.setStatus(403)
                )
            )

            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            .build();
    }
}