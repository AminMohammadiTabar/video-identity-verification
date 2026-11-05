package com.example.videokyc.auth.web;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.*;

class HomeControllerTest {
    @Test
    void homeShowsPublicLandingPageToGuest() {
        var model = new ExtendedModelMap();
        assertEquals("index", new HomeController().home(model, null));
        assertEquals(true, model.get("guest"));
        assertEquals(false, model.get("loggedIn"));
    }
}
