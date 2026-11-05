package com.example.videokyc.auth.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.domain.UserEntity;
import com.example.videokyc.auth.observability.AuthActionAudit;
import com.example.videokyc.auth.repository.RoleRepository;
import com.example.videokyc.auth.repository.UserRepository;

@Service
public class RegistrationService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Transactional
    public RegisteredUser register(String email, String rawPassword) {
        String normalized;
        try {
            normalized = normalizeEmail(email);
            validatePassword(rawPassword);
        } catch (IllegalArgumentException invalidInput) {
            AuthActionAudit.record("AUTH_REGISTER", "INVALID_INPUT", null);
            throw invalidInput;
        }
        if (users.existsByEmailIgnoreCase(normalized)) {
            AuthActionAudit.record("AUTH_REGISTER", "DUPLICATE", null);
            throw new EmailAlreadyRegisteredException();
        }
        var user = new UserEntity(normalized, encoder.encode(rawPassword));
        user.assignRole(roles.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER is missing from migrations")));
        try {
            var saved = users.saveAndFlush(user);
            UUID userId = saved.getId();
            // Log success only after the surrounding database transaction actually commits.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    AuthActionAudit.record("AUTH_REGISTER", "SUCCESS", userId);
                }
            });
            return new RegisteredUser(userId, saved.getEmail());
        } catch (DataIntegrityViolationException failure) {
            // SQLSTATE 23505 is UNIQUE_VIOLATION in both PostgreSQL and H2.
            // Never report a foreign key or storage failure as an existing email.
            if (isUniqueViolation(failure)) {
                AuthActionAudit.record("AUTH_REGISTER", "DUPLICATE", null);
                throw new EmailAlreadyRegisteredException();
            }
            throw failure;
        }
    }

    public static String normalizeEmail(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid email");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email");
        }
        return normalized;
    }

    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be 12–72 UTF-8 bytes");
        }
    }

    public record RegisteredUser(UUID id, String email) {}

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException() { super("This email is already registered"); }
    }
}
