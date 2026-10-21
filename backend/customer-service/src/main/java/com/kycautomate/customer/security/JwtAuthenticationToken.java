package com.kycautomate.customer.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public final class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID=1L;
    private final String subject;
    private final String tokenValue;
    public JwtAuthenticationToken(String subject,String tokenValue,Collection<? extends GrantedAuthority> authorities) {
        super(authorities); this.subject=subject; this.tokenValue=tokenValue; super.setAuthenticated(true);
    }
    @Override public Object getPrincipal() { return subject; }
    @Override public Object getCredentials() { return null; }
    @Override public String getName() { return subject; }
    public String getTokenValue() { return tokenValue; }
}