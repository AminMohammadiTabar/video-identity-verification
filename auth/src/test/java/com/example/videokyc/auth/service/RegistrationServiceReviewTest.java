package com.example.videokyc.auth.service;

import com.example.videokyc.auth.domain.RoleEntity;
import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.domain.UserEntity;
import com.example.videokyc.auth.repository.RoleRepository;
import com.example.videokyc.auth.repository.UserRepository;
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
    private final RegistrationService registration =
            new RegistrationService(users, roles, new BCryptPasswordEncoder(4));

    // ── existing tests (unchanged) ────────────────────────────────────────────

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
        String overLimit = "\uD83D\uDE00".repeat(19); // 19 code points, 76 UTF-8 bytes
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

    // ── NEW: password boundary scenarios ─────────────────────────────────────

    @Test
    void passwordOf11CharsIsRejected() {
        // One char below the minimum of 12 must be refused.
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationService.validatePassword("Short1234!x"));
    }

    @Test
    void passwordOf12CharsIsAccepted() {
        // Exactly 12 chars is the lower boundary — must pass.
        assertDoesNotThrow(() -> RegistrationService.validatePassword("Exactly12Chr"));
    }

    @Test
    void nullPasswordIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationService.validatePassword(null));
    }

    // ── NEW: email boundary scenarios ─────────────────────────────────────────

    @Test
    void nullEmailIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> registration.register(null, "SafePassword-123"));
        // DB must never be touched when input is null.
        verifyNoInteractions(users, roles);
    }

    @Test
    void emailOf321CharsIsRejected() {
        // local(64) + '@' + label(252) + '.com' = 321 chars — over the 320-char RFC limit.
        String tooLong = "a".repeat(64) + "@" + "b".repeat(252) + ".com";
        assertEquals(321, tooLong.length());
        assertThrows(IllegalArgumentException.class,
                () -> RegistrationService.normalizeEmail(tooLong));
    }

    @Test
    void emailOf320CharsIsAccepted() {
        // local(64) + '@' + label(251) + '.com' = 320 chars — exactly at the RFC limit.
        String email320 = "a".repeat(64) + "@" + "b".repeat(251) + ".com";
        assertEquals(320, email320.length());
        assertDoesNotThrow(() -> RegistrationService.normalizeEmail(email320));
    }

    // ── NEW: missing role-seed protection ────────────────────────────────────

    @Test
    void missingRoleUserSeedThrowsIllegalState() {
        // If Flyway migration has not seeded ROLE_USER, registration must fail loudly
        // rather than silently creating a role-less user.
        when(roles.findByName(RoleName.ROLE_USER)).thenReturn(Optional.empty());
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
        assertThrows(IllegalStateException.class,
                () -> registration.register("ok@example.com", "SafePassword-123"));
    }
}
