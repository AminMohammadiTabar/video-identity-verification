package com.example.videokyc.auth.config;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.domain.UserEntity;
import com.example.videokyc.auth.repository.RoleRepository;
import com.example.videokyc.auth.repository.UserRepository;
import com.example.videokyc.auth.service.RegistrationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/** Explicit one-time bootstrap; never automatically grants ADMIN to registered users. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                          @Value("${auth.bootstrap.enabled:false}") boolean enabled,
                          @Value("${auth.bootstrap.admin-email:}") String email,
                          @Value("${auth.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (users.countUsersWithRole(RoleName.ROLE_ADMIN) > 0) return;
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("AUTH_BOOTSTRAP_ADMIN_EMAIL is required");
        }
        String normalized = RegistrationService.normalizeEmail(email);
        RegistrationService.validatePassword(password);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new IllegalStateException("Admin bootstrap email already belongs to another user");
        }
        var admin = new UserEntity(normalized, encoder.encode(password));
        admin.assignRole(roles.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN missing")));
        users.saveAndFlush(admin);
    }
}
