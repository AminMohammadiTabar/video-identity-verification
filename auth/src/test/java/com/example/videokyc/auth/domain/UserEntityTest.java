package com.example.videokyc.auth.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTest {
    @Test
    void emailIsNormalizedForCaseInsensitiveLookup() {
        UserEntity user = new UserEntity(" Test.User@Example.COM ", "encoded-hash");
        assertEquals("test.user@example.com", user.getEmail());
        assertTrue(user.isEnabled());
    }

    @Test
    void blankEmailIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> new UserEntity(" ", "encoded-hash"));
    }
}
