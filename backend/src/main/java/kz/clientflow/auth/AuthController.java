package kz.clientflow.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kz.clientflow.auth.dto.AuthRequests;
import kz.clientflow.user.UserService;
import kz.clientflow.user.dto.MeResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final UserService userService;

    public AuthController(AuthService authService, SessionService sessionService, UserService userService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.userService = userService;
    }

    /** Issues the XSRF-TOKEN cookie. The SPA calls this once before its first POST. */
    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        authService.register(body, request.getRemoteAddr());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody AuthRequests.TokenOnly body) {
        authService.verifyEmail(body.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendVerification(@Valid @RequestBody AuthRequests.EmailOnly body, HttpServletRequest request) {
        authService.resendVerification(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody AuthRequests.Login body,
                     HttpServletRequest request, HttpServletResponse response) {
        CurrentUser user = authService.authenticate(body, request.getRemoteAddr());
        sessionService.start(user, request, response);
        return userService.getMe(user.id());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        sessionService.end(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody AuthRequests.EmailOnly body, HttpServletRequest request) {
        authService.forgotPassword(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword body) {
        authService.resetPassword(body);
    }
}
