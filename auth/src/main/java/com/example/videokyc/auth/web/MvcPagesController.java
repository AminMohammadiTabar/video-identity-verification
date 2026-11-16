package com.example.videokyc.auth.web;

import com.example.videokyc.auth.domain.VerificationStatus;
import com.example.videokyc.auth.service.RegistrationService;
import com.example.videokyc.auth.service.VerificationService;
import com.example.videokyc.auth.service.VerificationException;
import com.example.videokyc.auth.observability.AuthActionAudit;
import com.example.videokyc.auth.observability.audit.AuditEventCommand;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.UUID;

@Controller
public class MvcPagesController {
    private final RegistrationService registration;
    private final VerificationService verifications;
    private final AuditEventRecorder audit;

    public MvcPagesController(RegistrationService registration, VerificationService verifications,
                              AuditEventRecorder audit) {
        this.registration = registration;
        this.verifications = verifications;
        this.audit = audit;
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
            audit.recordIndependent(AuditEventCommand.of("auth.user.registration_rejected", "ANONYMOUS", null,
                    null, null, "USER_ACCOUNT", null, "DENIED", "INVALID_INPUT", null, null));
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
    String upload(Authentication authentication, Model model, HttpServletRequest request,
                  @RequestParam(name = "submitted", required = false) String submitted) {
        UUID userId = AuthActionAudit.userId(authentication);
        var status = userId == null ? VerificationStatus.NOT_SUBMITTED : verifications.mine(userId).status();
        model.addAttribute("email", authentication.getName());
        var latest = userId == null ? null : verifications.mine(userId);
        model.addAttribute("verificationStatus", status.name());
        model.addAttribute("attemptNumber", latest == null ? 0 : latest.attemptNumber());
        model.addAttribute("hasLatestAttempt", latest != null && latest.id() != null);
        model.addAttribute("reviewReason", latest == null ? null : latest.reviewReason());
        model.addAttribute("allowUpload", status == VerificationStatus.NOT_SUBMITTED ||
                status == VerificationStatus.UPLOAD_FAILED || status == VerificationStatus.REJECTED);
        model.addAttribute("waiting", status == VerificationStatus.PENDING_REVIEW);
        model.addAttribute("approved", status == VerificationStatus.APPROVED);
        model.addAttribute("rejected", status == VerificationStatus.REJECTED);
        var history = userId == null ? java.util.List.<VerificationService.VerificationView>of() : verifications.history(userId);
        model.addAttribute("hasAttempts", !history.isEmpty());
        model.addAttribute("attempts", history.stream()
                .map(v -> Map.of("attemptNumber", v.attemptNumber(), "status", v.status().name(),
                        "submittedAt", v.submittedAt() == null ? "-" : v.submittedAt().toString(),
                        "reviewedAt", v.reviewedAt() == null ? "-" : v.reviewedAt().toString(),
                        "reviewReason", v.reviewReason() == null ? "-" : v.reviewReason())).toList());
        model.addAttribute("submitted", submitted != null);
        addCsrf(model, request);
        return "verification/upload";
    }

    @PostMapping("/verification/upload")
    String submitVideo(Authentication authentication, @RequestParam("video") MultipartFile video,
                       Model model, HttpServletRequest request) {
        UUID userId = AuthActionAudit.userId(authentication);
        if (userId == null) throw new VerificationException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Authentication is required");
        try {
            verifications.submit(userId, video);
            return "redirect:/verification/upload?submitted";
        } catch (VerificationException e) {
            model.addAttribute("email", authentication.getName());
            var latest = verifications.mine(userId);
            model.addAttribute("verificationStatus", latest.status().name());
            model.addAttribute("attemptNumber", latest.attemptNumber());
            model.addAttribute("hasLatestAttempt", latest.id() != null);
            model.addAttribute("reviewReason", latest.reviewReason());
            model.addAttribute("allowUpload", latest.status() == VerificationStatus.NOT_SUBMITTED ||
                    latest.status() == VerificationStatus.UPLOAD_FAILED || latest.status() == VerificationStatus.REJECTED);
            model.addAttribute("waiting", latest.status() == VerificationStatus.PENDING_REVIEW);
            model.addAttribute("approved", latest.status() == VerificationStatus.APPROVED);
            model.addAttribute("rejected", latest.status() == VerificationStatus.REJECTED);
            var history = verifications.history(userId);
            model.addAttribute("hasAttempts", !history.isEmpty());
            model.addAttribute("attempts", history.stream()
                    .map(v -> Map.of("attemptNumber", v.attemptNumber(), "status", v.status().name(),
                            "submittedAt", v.submittedAt() == null ? "-" : v.submittedAt().toString(),
                            "reviewedAt", v.reviewedAt() == null ? "-" : v.reviewedAt().toString(),
                            "reviewReason", v.reviewReason() == null ? "-" : v.reviewReason())).toList());
            model.addAttribute("uploadError", e.getMessage());
            addCsrf(model, request);
            return "verification/upload";
        }
    }

    @GetMapping("/admin")
    String admin(Authentication authentication, Model model, HttpServletRequest request,
                 @RequestParam(name = "updated", required = false) String updated,
                 @RequestParam(name = "error", required = false) String error) {
        model.addAttribute("email", authentication.getName());
        model.addAttribute("updated", updated != null);
        model.addAttribute("reviewError", error != null);
        model.addAttribute("requests", verifications.pending().stream()
                .map(v -> Map.of("id", v.id().toString(), "attemptNumber", v.attemptNumber(),
                        "userEmail", v.userEmail(), "submittedAt", v.submittedAt().toString())).toList());
        addCsrf(model, request);
        return "auth/admin";
    }

    @PostMapping("/admin/verifications/{id}/approve")
    String approve(Authentication authentication, @org.springframework.web.bind.annotation.PathVariable UUID id) {
        var reviewerId = AuthActionAudit.userId(authentication);
        if (reviewerId == null) throw new VerificationException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        try {
            verifications.decide(id, reviewerId, true, null);
            return "redirect:/admin?updated";
        } catch (VerificationException error) {
            return "redirect:/admin?error";
        }
    }

    @PostMapping("/admin/verifications/{id}/reject")
    String reject(Authentication authentication, @org.springframework.web.bind.annotation.PathVariable UUID id,
                  @RequestParam("reason") String reason) {
        var reviewerId = AuthActionAudit.userId(authentication);
        if (reviewerId == null) throw new VerificationException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        try {
            verifications.decide(id, reviewerId, false, reason);
            return "redirect:/admin?updated";
        } catch (VerificationException error) {
            return "redirect:/admin?error";
        }
    }

    static void addCsrf(Model model, HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute("_csrf");
        if (csrf != null) {
            model.addAttribute("csrfToken", csrf.getToken());
            model.addAttribute("csrfParameter", csrf.getParameterName());
        }
    }
}
