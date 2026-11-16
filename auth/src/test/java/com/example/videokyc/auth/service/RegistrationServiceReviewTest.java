package com.example.videokyc.auth.service;

import com.example.videokyc.auth.domain.RoleEntity;
import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.domain.UserEntity;
import com.example.videokyc.auth.repository.RoleRepository;
import com.example.videokyc.auth.repository.UserRepository;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationServiceReviewTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AuditEventRecorder audit = mock(AuditEventRecorder.class);
    private final RegistrationService registration =
            new RegistrationService(users, roles, new BCryptPasswordEncoder(4), audit);

    @Test
    void normalizesAndChecksEmailAtServiceBoundary() {
        assertEquals("person@example.com", RegistrationService.normalizeEmail("  PERSON@EXAMPLE.COM "));
        for (String bad : new String[] { "abc", "@example.com", "a@example", "a@@example.com", "a b@example.com" }) {
            assertThrows(IllegalArgumentException.class, () ->
                    registration.register(bad, "SafePassword-123"));
        }
        verifyNoInteractions(users, roles);
    }

    @Test
    void enforcesBcryptByteLimitOnUnicodePasswords() {
        String overLimit = "😀".repeat(19); // 19 code points, 76 UTF-8 bytes
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationService.validatePassword(overLimit));
        assertDoesNotThrow(() -> RegistrationService.validatePassword("SafePassword-123"));
    }

    @Test
    void uniqueViolationBecomesDuplicateRegistration() {
        when(roles.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(new RoleEntity(RoleName.ROLE_USER)));
        when(users.saveAndFlush(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException(
                "unique index", new SQLException("duplicate email", "23505")));
        assertThrows(RegistrationService.EmailAlreadyRegisteredException.class,
                () -> registration.register("person@example.com", "SafePassword-123"));
    }

    @Test
    void unrelatedDatabaseFailureIsNotMisreportedAsDuplicate() {
        when(roles.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(new RoleEntity(RoleName.ROLE_USER)));
        DataIntegrityViolationException storageFailure = new DataIntegrityViolationException(
                "foreign key", new SQLException("foreign key violation", "23503"));
        when(users.saveAndFlush(any(UserEntity.class))).thenThrow(storageFailure);
        assertSame(storageFailure, assertThrows(DataIntegrityViolationException.class,
                () -> registration.register("person@example.com", "SafePassword-123")));
    }
}
