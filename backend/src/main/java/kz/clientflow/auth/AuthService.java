package kz.clientflow.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.auth.dto.AuthRequests;
import kz.clientflow.auth.token.PasswordResetToken;
import kz.clientflow.auth.token.PasswordResetTokenRepository;
import kz.clientflow.auth.token.TokenGenerator;
import kz.clientflow.auth.token.VerificationToken;
import kz.clientflow.auth.token.VerificationTokenRepository;
import kz.clientflow.common.ApiException;
import kz.clientflow.config.AppProperties;
import kz.clientflow.mail.AuthEmail;
import kz.clientflow.user.User;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.Workspace;
import kz.clientflow.workspace.WorkspaceMembership;
import kz.clientflow.workspace.WorkspaceMembershipRepository;
import kz.clientflow.workspace.WorkspaceRepository;
import kz.clientflow.workspace.WorkspaceRole;
import kz.clientflow.workspace.WorkspaceService;

/**
 * Registration, email verification, login and password recovery.
 *
 * Responses for register / resend / forgot-password are identical whether
 * or not the email exists, so the API cannot be used to discover accounts.
 */
@Service
public class AuthService {

    private static final Duration WINDOW_15_MIN = Duration.ofMinutes(15);
    private static final Duration WINDOW_1_HOUR = Duration.ofHours(1);

    private final UserRepository users;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RateLimiter rateLimiter;
    private final SessionService sessionService;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final WorkspaceService workspaceService;

    public AuthService(UserRepository users, WorkspaceRepository workspaces,
                       WorkspaceMembershipRepository memberships,
                       VerificationTokenRepository verificationTokens,
                       PasswordResetTokenRepository resetTokens, TokenGenerator tokenGenerator,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       RateLimiter rateLimiter, SessionService sessionService,
                       ApplicationEventPublisher events, AppProperties properties,
                       WorkspaceService workspaceService) {
        this.users = users;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.tokenGenerator = tokenGenerator;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.sessionService = sessionService;
        this.events = events;
        this.properties = properties;
        this.workspaceService = workspaceService;
    }

    /** Creates the user with a personal workspace (role OWNER) and sends a verification email. */
    @Transactional
    public void register(AuthRequests.Register request, String clientIp) {
        rateLimiter.consume("register:ip:" + clientIp, 10, WINDOW_1_HOUR);

        String email = normalizeEmail(request.email());
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isEmailVerified()) {
                publish(AuthEmail.Type.ACCOUNT_EXISTS, user, null);
            } else {
                sendNewVerificationToken(user);
            }
            return;
        }

        String language = request.language() == null ? "ru" : request.language();
        User user = users.save(new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), language));

        Workspace workspace = workspaces.save(new Workspace(user.getFullName()));
        memberships.save(new WorkspaceMembership(workspace, user, WorkspaceRole.OWNER));

        sendNewVerificationToken(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = Instant.now();
        VerificationToken token = verificationTokens.findByTokenHash(tokenGenerator.hash(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN"));

        token.markUsed(now);
        token.getUser().markEmailVerified();
        verificationTokens.invalidateAllForUser(token.getUser().getId(), now);
        workspaceService.acceptPendingInvitations(token.getUser());
    }

    @Transactional
    public void resendVerification(String rawEmail, String clientIp) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.consume("resend:ip:" + clientIp, 10, WINDOW_1_HOUR);
        rateLimiter.consume("resend:email:" + tokenGenerator.hash(email), 3, WINDOW_1_HOUR);

        users.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendNewVerificationToken);
    }

    /**
     * Checks the credentials. The caller (controller) then starts the session.
     * Wrong email and wrong password produce the same error; the password hash is
     * still computed for unknown emails, so response time does not reveal accounts.
     */
    @Transactional(readOnly = true)
    public CurrentUser authenticate(AuthRequests.Login request, String clientIp) {
        String email = normalizeEmail(request.email());
        rateLimiter.consume("login:ip:" + clientIp, 30, WINDOW_15_MIN);
        rateLimiter.consume("login:email:" + tokenGenerator.hash(email), 10, WINDOW_15_MIN);

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        User user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
        }
        return new CurrentUser(user.getId(), user.getEmail());
    }

    @Transactional
    public void forgotPassword(String rawEmail, String clientIp) {
        String email = normalizeEmail(rawEmail);
        rateLimiter.consume("reset:ip:" + clientIp, 10, WINDOW_1_HOUR);
        rateLimiter.consume("reset:email:" + tokenGenerator.hash(email), 3, WINDOW_1_HOUR);

        users.findByEmail(email).ifPresent(user -> {
            Instant now = Instant.now();
            resetTokens.invalidateAllForUser(user.getId(), now);
            TokenGenerator.GeneratedToken token = tokenGenerator.generate();
            resetTokens.save(new PasswordResetToken(user, token.hash(),
                    now.plus(properties.tokens().passwordResetTtl())));
            publish(AuthEmail.Type.PASSWORD_RESET, user, token.raw());
        });
    }

    /**
     * Sets a new password from a reset link. All sessions of the user are revoked
     * and the user is NOT signed in automatically.
     */
    @Transactional
    public void resetPassword(AuthRequests.ResetPassword request) {
        Instant now = Instant.now();
        PasswordResetToken token = resetTokens.findByTokenHash(tokenGenerator.hash(request.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN"));

        User user = token.getUser();
        token.markUsed(now);
        resetTokens.invalidateAllForUser(user.getId(), now);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Opening the link proves access to the mailbox
        user.markEmailVerified();
        workspaceService.acceptPendingInvitations(user);
        sessionService.revokeAll(user.getId());
    }

    private void sendNewVerificationToken(User user) {
        Instant now = Instant.now();
        verificationTokens.invalidateAllForUser(user.getId(), now);
        TokenGenerator.GeneratedToken token = tokenGenerator.generate();
        verificationTokens.save(new VerificationToken(user, token.hash(),
                now.plus(properties.tokens().verificationTtl())));
        publish(AuthEmail.Type.VERIFY_EMAIL, user, token.raw());
    }

    private void publish(AuthEmail.Type type, User user, String rawToken) {
        events.publishEvent(new AuthEmail(type, user.getEmail(), user.getFullName(),
                user.getPreferredLanguage(), rawToken));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
