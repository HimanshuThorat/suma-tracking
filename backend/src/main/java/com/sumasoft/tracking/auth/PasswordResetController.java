package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.attendance.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password reset by email (Step 43). These three are open without signing in:
 *   POST /api/auth/forgot-password        {email}
 *   GET  /api/auth/reset-password/check   ?token=...
 *   POST /api/auth/reset-password         {token, newPassword}
 */
@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    public record ForgotRequest(@NotBlank(message = "Enter your email") @Size(max = 254, message = "Email is too long") String email) {
    }

    public record ResetRequest(
            @NotBlank(message = "The reset link is missing. Open the link from your email again.") @Size(max = 100) String token,
            @NotBlank(message = "Enter a new password") @Size(max = 200, message = "Password is too long") String newPassword) {
    }

    public record Message(String message) {
    }

    @PostMapping("/forgot-password")
    public Message forgot(@Valid @RequestBody ForgotRequest body, HttpServletRequest request) {
        return new Message(service.requestLink(body.email(), AttendanceService.clientIp(request)));
    }

    @GetMapping("/reset-password/check")
    public PasswordResetService.CheckResult check(@RequestParam(required = false) String token) {
        return service.check(token);
    }

    @PostMapping("/reset-password")
    public Message reset(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        return new Message(service.reset(body.token(), body.newPassword(), AttendanceService.clientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT)));
    }
}
