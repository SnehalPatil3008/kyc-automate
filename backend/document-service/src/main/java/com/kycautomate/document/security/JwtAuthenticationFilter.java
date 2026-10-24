package com.kycautomate.document.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

// Registered inside Spring Security only; deliberately not a component/global servlet filter.
public final class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtCodec codec;
    private final UserDetailsService users;
    public JwtAuthenticationFilter(JwtCodec codec,UserDetailsService users) { this.codec=codec;this.users=users; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath();
        return path.equals("/api/auth/login") || path.equals("/api/auth/register")
            || path.equals("/api/reviewer-auth/login") || path.equals("/actuator/health");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if(header==null || !header.regionMatches(true,0,"Bearer ",0,7)) { chain.doFilter(request,response);return; }
        try {
            if(Collections.list(request.getHeaders("Authorization")).size()!=1)
                throw new JwtException("Multiple authorization headers");
            String value=header.substring(7).trim();
            var identity=codec.verify(value);
            var authorities=List.of(new SimpleGrantedAuthority("ROLE_"+identity.role()));
            JwtAuthenticationToken authentication;
            if(users!=null) {
                var user=users.loadUserByUsername(identity.subject());
                if(!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                        || !user.isCredentialsNonExpired()) throw new JwtException("Inactive account");
                authentication=new JwtAuthenticationToken(user.getUsername(),value,user.getAuthorities());
            } else authentication=new JwtAuthenticationToken(identity.subject(),value,authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch(JwtException | IllegalArgumentException | AuthenticationException ex) {
            SecurityContextHolder.clearContext(); response.setStatus(401);
            response.setHeader("WWW-Authenticate","Bearer"); response.setContentType("application/json");
            response.getWriter().write("""
                {"error":"Invalid or expired access token"}
                """); return;
        }
        chain.doFilter(request,response);
    }
}
