package kz.clientflow.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kz.clientflow.common.ApiException;

/**
 * Starts, ends, lists and revokes server-side sessions (stored in PostgreSQL by Spring Session).
 */
@Service
public class SessionService {

    private static final String USER_AGENT = "CF_USER_AGENT";
    private static final String IP_ADDRESS = "CF_IP";

    /**
     * A session as shown to its owner. The real session id is never returned:
     * it is the value of the HttpOnly cookie. "key" is a hash of it instead.
     */
    public record SessionInfo(String key, Instant createdAt, Instant lastAccessedAt, String userAgent,
                              String ipAddress, boolean current) {
    }

    private final SecurityContextRepository securityContextRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionService(SecurityContextRepository securityContextRepository,
                          FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.securityContextRepository = securityContextRepository;
        this.sessions = sessions;
    }

    public void start(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
        // Always begin with a fresh session id (protection against session fixation)
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        String userAgent = request.getHeader("User-Agent");
        session.setAttribute(USER_AGENT, userAgent == null ? null
                : userAgent.substring(0, Math.min(userAgent.length(), 200)));
        session.setAttribute(IP_ADDRESS, request.getRemoteAddr());

        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    public List<SessionInfo> list(UUID userId, String currentSessionId) {
        return sessions.findByPrincipalName(userId.toString()).values().stream()
                .map(s -> new SessionInfo(keyOf(s.getId()), s.getCreationTime(), s.getLastAccessedTime(),
                        s.getAttribute(USER_AGENT), s.getAttribute(IP_ADDRESS), s.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionInfo::lastAccessedAt).reversed())
                .toList();
    }

    /** Signs out one session of this user (found by its key). */
    public void revoke(UUID userId, String key) {
        String sessionId = sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> keyOf(id).equals(key))
                .findFirst()
                .orElseThrow(ApiException::notFound);
        sessions.deleteById(sessionId);
    }

    /** Signs the user out everywhere (password reset, account deletion). */
    public void revokeAll(UUID userId) {
        sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
    }

    /** Signs the user out everywhere except the current session (password change). */
    public void revokeAllExcept(UUID userId, String currentSessionId) {
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(currentSessionId))
                .forEach(sessions::deleteById);
    }

    static String keyOf(String sessionId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
