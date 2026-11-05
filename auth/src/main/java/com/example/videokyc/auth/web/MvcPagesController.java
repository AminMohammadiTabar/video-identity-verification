package com.example.videokyc.auth.web;

import com.example.videokyc.auth.domain.VerificationStatus;
import com.example.videokyc.auth.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MvcPagesController {
    private final RegistrationService registration;

    public MvcPagesController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/login")
    String login(@RequestParam(name = "error", required = false) String error,
                 @RequestParam(name = "logout", required = false) String logout,
                 @RequestParam(name = "registered", required = false) String registered,
                 Model model, HttpServletRequest request) {
        addCsrf(model, request);
        model.addAttribute("loginError", error != null);
        model.addAttribute("loggedOut", logout != null);
        model.addAttribute("registered", registered != null);
        return "auth/login";
    }

    @GetMapping("/register")
    String registrationForm(Model model, HttpServletRequest request) {
        model.addAttribute("email", "");
        addCsrf(model, request);
        return "auth/register";
    }

    @PostMapping("/register")
    String submitRegistration(@Valid @ModelAttribute RegistrationForm form,
                              BindingResult errors, Model model, HttpServletRequest request) {
        addCsrf(model, request);
        model.addAttribute("email", form.getEmail() == null ? "" : form.getEmail());
        if (errors.hasErrors()) {
            model.addAttribute("registrationError", "ایمیل معتبر و رمز عبور ۱۲ تا ۷۲ کاراکتری وارد کنید.");
            return "auth/register";
        }
        try {
            registration.register(form.getEmail(), form.getPassword());
        } catch (RegistrationService.EmailAlreadyRegisteredException e) {
            model.addAttribute("registrationError", "این ایمیل قبلاً ثبت شده است.");
            return "auth/register";
        } catch (IllegalArgumentException e) {
            model.addAttribute("registrationError", "رمز عبور باید حداقل ۱۲ و حداکثر ۷۲ بایت UTF-8 باشد.");
            return "auth/register";
        }
        return "redirect:/login?registered";
    }

    @GetMapping("/verification/upload")
    String uploadPlaceholder(Authentication authentication, Model model, HttpServletRequest request) {
        model.addAttribute("email", authentication.getName());
        model.addAttribute("verificationStatus", VerificationStatus.NOT_SUBMITTED.name());
        addCsrf(model, request);
        return "verification/upload";
    }

    @GetMapping("/admin")
    String admin(Authentication authentication, Model model, HttpServletRequest request) {
        model.addAttribute("email", authentication.getName());
        addCsrf(model, request);
        return "auth/admin";
    }

    static void addCsrf(Model model, HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
        if (csrf != null) {
            model.addAttribute("csrfToken", csrf.getToken());
            model.addAttribute("csrfParameter", csrf.getParameterName());
        }
    }
}
