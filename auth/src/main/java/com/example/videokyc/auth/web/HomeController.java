package com.example.videokyc.auth.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home(Model model, Authentication auth) {
        boolean loggedIn = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        model.addAttribute("projectName", "Video Identity Verification");
        model.addAttribute("loggedIn", loggedIn);
        model.addAttribute("guest", !loggedIn);
        return "index";
    }
}
