package kz.clientflow.workspace;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.auth.RateLimiter;
import kz.clientflow.common.ApiException;
import kz.clientflow.mail.InvitationEmail;
import kz.clientflow.notification.NotificationRepository;
import kz.clientflow.order.OrderRepository;
import kz.clientflow.user.User;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.dto.WorkspaceDtos.InvitationResponse;
import kz.clientflow.workspace.dto.WorkspaceDtos.MemberResponse;
import kz.clientflow.workspace.dto.WorkspaceDtos.MembersResponse;
import kz.clientflow.workspace.dto.WorkspaceDtos.WorkspaceResponse;

/**
 * Workspace settings and membership. Only the OWNER manages members.
 * Each workspace has exactly one OWNER (its creator); invited members are
 * MANAGER or EMPLOYEE.
 */
@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceInvitationRepository invitations;
    private final UserRepository users;
    private final OrderRepository orders;
    private final ActivityService activity;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final NotificationRepository notifications;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMembershipRepository memberships,
                            WorkspaceInvitationRepository invitations, UserRepository users,
                            OrderRepository orders, ActivityService activity, RateLimiter rateLimiter,
                            ApplicationEventPublisher events, NotificationRepository notifications) {
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
        this.orders = orders;
        this.activity = activity;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(WorkspaceAccess access) {
        Workspace workspace = workspaces.findById(access.workspaceId()).orElseThrow(ApiException::notFound);
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), access.role());
    }

    @Transactional
    public WorkspaceResponse rename(WorkspaceAccess access, String name) {
        requireOwner(access);
        Workspace workspace = workspaces.findById(access.workspaceId()).orElseThrow(ApiException::notFound);
        workspace.setName(name.trim());
        activity.record(access, "WORKSPACE_RENAMED", ActivityLog.EntityType.WORKSPACE, workspace.getId(),
                Map.of("name", workspace.getName()));
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), access.role());
    }

    @Transactional(readOnly = true)
    public Requisites requisites(WorkspaceAccess access) {
        return workspaces.findById(access.workspaceId()).orElseThrow(ApiException::notFound).getRequisites();
    }

    @Transactional
    public Requisites updateRequisites(WorkspaceAccess access, Requisites requisites) {
        requireOwner(access);
        Workspace workspace = workspaces.findById(access.workspaceId()).orElseThrow(ApiException::notFound);
        Requisites normalized = requisites.normalized();
        if (!normalized.isValidNormalized()) {
            throw ApiException.badRequest("VALIDATION_FAILED");
        }
        workspace.setRequisites(normalized);
        activity.record(access, "REQUISITES_UPDATED", ActivityLog.EntityType.WORKSPACE, workspace.getId(), Map.of());
        return workspace.getRequisites();
    }

    @Transactional(readOnly = true)
    public MembersResponse members(WorkspaceAccess access) {
        List<MemberResponse> members = memberships.findByWorkspaceIdOrderByCreatedAtAsc(access.workspaceId())
                .stream()
                .map(m -> new MemberResponse(m.getUser().getId(), m.getUser().getFullName(), m.getUser().getEmail(),
                        m.getRole(), m.getCreatedAt()))
                .toList();
        List<InvitationResponse> pending = !access.role().canManageMembers() ? List.of()
                : invitations.findByWorkspaceIdOrderByCreatedAtAsc(access.workspaceId()).stream()
                .map(i -> new InvitationResponse(i.getId(), i.getEmail(), i.getRole(), i.getCreatedAt()))
                .toList();
        return new MembersResponse(members, pending);
    }

    /**
     * Adds a verified user immediately; otherwise stores a pending invitation that
     * is accepted when someone confirms this email. The response is the same in
     * both cases, so the endpoint cannot be used to check whether an account exists.
     */
    @Transactional
    public void invite(WorkspaceAccess access, String rawEmail, WorkspaceRole role) {
        requireOwner(access);
        requireInvitableRole(role);
        rateLimiter.consume("invite:user:" + access.userId(), 30, Duration.ofHours(1));

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Workspace workspace = workspaces.findById(access.workspaceId()).orElseThrow(ApiException::notFound);
        User inviter = users.findById(access.userId()).orElseThrow(ApiException::notFound);

        var existingUser = users.findByEmail(email).filter(User::isEmailVerified);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (memberships.existsByWorkspaceIdAndUserId(workspace.getId(), user.getId())) {
                return;
            }
            memberships.save(new WorkspaceMembership(workspace, user, role));
            activity.record(access, "MEMBER_ADDED", ActivityLog.EntityType.MEMBER, user.getId(),
                    Map.of("email", email, "role", role.name()));
        } else {
            invitations.findByWorkspaceIdAndEmail(workspace.getId(), email).ifPresentOrElse(
                    existing -> existing.setRole(role),
                    () -> invitations.save(new WorkspaceInvitation(workspace, email, role, access.userId())));
        }
        events.publishEvent(new InvitationEmail(email, workspace.getName(), inviter.getFullName(),
                inviter.getPreferredLanguage()));
    }

    @Transactional
    public void changeRole(WorkspaceAccess access, UUID userId, WorkspaceRole role) {
        requireOwner(access);
        requireInvitableRole(role);
        if (userId.equals(access.userId())) {
            throw ApiException.badRequest("CANNOT_CHANGE_OWN_ROLE");
        }
        WorkspaceMembership membership = memberships.findByWorkspaceIdAndUserId(access.workspaceId(), userId)
                .orElseThrow(ApiException::notFound);
        if (membership.getRole() != role) {
            WorkspaceRole previous = membership.getRole();
            membership.setRole(role);
            activity.record(access, "MEMBER_ROLE_CHANGED", ActivityLog.EntityType.MEMBER, userId,
                    Map.of("from", previous.name(), "to", role.name()));
        }
    }

    /** Removes a member; their orders in this workspace become unassigned. */
    @Transactional
    public void removeMember(WorkspaceAccess access, UUID userId) {
        requireOwner(access);
        if (userId.equals(access.userId())) {
            throw ApiException.badRequest("CANNOT_REMOVE_SELF");
        }
        WorkspaceMembership membership = memberships.findByWorkspaceIdAndUserId(access.workspaceId(), userId)
                .orElseThrow(ApiException::notFound);
        orders.unassignAll(access.workspaceId(), userId);
        notifications.deleteForMember(access.workspaceId(), userId);
        memberships.delete(membership);
        activity.record(access, "MEMBER_REMOVED", ActivityLog.EntityType.MEMBER, userId, Map.of());
    }

    @Transactional
    public void cancelInvitation(WorkspaceAccess access, UUID invitationId) {
        requireOwner(access);
        WorkspaceInvitation invitation = invitations.findByIdAndWorkspaceId(invitationId, access.workspaceId())
                .orElseThrow(ApiException::notFound);
        invitations.delete(invitation);
    }

    /** Called when a user confirms their email: pending invitations become memberships. */
    @Transactional
    public void acceptPendingInvitations(User user) {
        for (WorkspaceInvitation invitation : invitations.findByEmail(user.getEmail())) {
            Workspace workspace = invitation.getWorkspace();
            if (!memberships.existsByWorkspaceIdAndUserId(workspace.getId(), user.getId())) {
                memberships.save(new WorkspaceMembership(workspace, user, invitation.getRole()));
                activity.record(new WorkspaceAccess(workspace.getId(), user.getId(), invitation.getRole()),
                        "MEMBER_JOINED", ActivityLog.EntityType.MEMBER, user.getId(),
                        Map.of("role", invitation.getRole().name()));
            }
            invitations.delete(invitation);
        }
    }

    private static void requireOwner(WorkspaceAccess access) {
        if (!access.role().canManageMembers()) {
            throw ApiException.forbidden();
        }
    }

    private static void requireInvitableRole(WorkspaceRole role) {
        if (role == WorkspaceRole.OWNER) {
            throw ApiException.badRequest("INVALID_ROLE");
        }
    }
}
