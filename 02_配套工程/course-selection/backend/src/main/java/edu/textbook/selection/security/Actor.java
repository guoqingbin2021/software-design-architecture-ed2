package edu.textbook.selection.security;

import edu.textbook.selection.api.ApiFailure;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

public final class Actor implements UserDetails {
    private final String username,passwordHash,role,name;
    private final long personId;
    public Actor(String username,String passwordHash,String role,long personId,String name) {
        this.username=username; this.passwordHash=passwordHash; this.role=role; this.personId=personId; this.name=name;
    }
    public void requireRole(String required) { if(!role.equals(required)) throw ApiFailure.forbidden(); }
    public String role() { return role; }
    public long personId() { return personId; }
    public String name() { return name; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_"+role)); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
