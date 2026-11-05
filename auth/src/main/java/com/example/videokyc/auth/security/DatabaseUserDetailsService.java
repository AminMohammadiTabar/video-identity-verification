package com.example.videokyc.auth.security;

import com.example.videokyc.auth.domain.UserEntity;
import com.example.videokyc.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DatabaseUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        UserEntity entity = users.findByEmailIgnoreCase(username.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        String[] authorities = entity.getRoles().stream()
                .map(role -> role.getName().name())
                .toArray(String[]::new);
        UserDetails springPrincipal = User.withUsername(entity.getEmail())
                .password(entity.getPasswordHash())
                .disabled(!entity.isEnabled())
                .authorities(authorities)
                .build();
        return new AccountPrincipal(entity.getId(), springPrincipal);
    }
}
