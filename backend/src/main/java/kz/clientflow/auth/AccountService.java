package kz.clientflow.auth;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.common.ApiException;
import kz.clientflow.user.User;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.WorkspaceMembership;
import kz.clientflow.workspace.WorkspaceMembershipRepository;
import kz.clientflow.workspace.WorkspaceRole;

/**
 * Security-sensitive account operations. Each one requires the current password.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final WorkspaceMembershipRepository memberships;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final SessionService sessionService;
    private final JdbcTemplate jdbc;

    public AccountService(UserRepository users, WorkspaceMembershipRepository memberships,
                          PasswordEncoder passwordEncoder, RateLimiter rateLimiter, SessionService sessionService,
                          JdbcTemplate jdbc) {
        this.users = users;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.sessionService = sessionService;
        this.jdbc = jdbc;
    }

    /** Changes the password and signs out every other session of the user. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String currentSessionId) {
        User user = requirePassword(userId, currentPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sessionService.revokeAllExcept(userId, currentSessionId);
    }

    /**
     * Deletes the account. What happens to the data:
     * - workspaces where the user is OWNER are deleted completely, including their
     *   clients, orders, payments, notes, history and the memberships of other people;
     * - in other workspaces the membership is removed, the user's orders become
     *   unassigned and their notifications are deleted;
     * - history entries keep the change but lose the author (shown as "deleted user"),
     *   and the user's email is removed from history details;
     * - all sessions, tokens, the avatar and the user row are deleted.
     */
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = requirePassword(userId, password);

        List<WorkspaceMembership> all = memberships.findByUserIdOrderByCreatedAtAsc(userId);
        for (WorkspaceMembership membership : all) {
            UUID workspaceId = membership.getWorkspace().getId();
            if (membership.getRole() == WorkspaceRole.OWNER) {
                // ON DELETE CASCADE removes everything that belongs to the workspace
                jdbc.update("DELETE FROM workspaces WHERE id = ?", workspaceId);
            } else {
                jdbc.update("UPDATE customer_orders SET assigned_user_id = NULL WHERE workspace_id = ? "
                        + "AND assigned_user_id = ?", workspaceId, userId);
                jdbc.update("DELETE FROM notifications WHERE workspace_id = ? AND user_id = ?", workspaceId, userId);
            }
        }
        jdbc.update("UPDATE activity_logs SET metadata = metadata - 'email' WHERE metadata->>'email' = ?",
                user.getEmail());
        jdbc.update("DELETE FROM workspace_invitations WHERE email = ?", user.getEmail());
        sessionService.revokeAll(userId);
        // Remaining references (payments.created_by, notes, history) are set to NULL by the foreign keys
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private User requirePassword(UUID userId, String password) {
        rateLimiter.consume("password-check:user:" + userId, 10, Duration.ofHours(1));
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD");
        }
        return user;
    }
}
