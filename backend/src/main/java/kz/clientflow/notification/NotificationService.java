package kz.clientflow.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.common.ApiException;
import kz.clientflow.common.PageResponse;
import kz.clientflow.order.CustomerOrder;
import kz.clientflow.user.User;
import kz.clientflow.user.UserRepository;
import kz.clientflow.workspace.WorkspaceAccess;
import kz.clientflow.workspace.WorkspaceMembership;
import kz.clientflow.workspace.WorkspaceMembershipRepository;

/** Reading notifications and creating the "order changed" ones. */
@Service
public class NotificationService {

    public record NotificationResponse(UUID id, Notification.Type type, UUID orderId, Map<String, Object> payload,
                                       boolean read, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getOrderId(), n.getPayload(), n.isRead(),
                    n.getCreatedAt());
        }
    }

    private final NotificationRepository notifications;
    private final WorkspaceMembershipRepository memberships;
    private final UserRepository users;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, WorkspaceMembershipRepository memberships,
                               UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.memberships = memberships;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(WorkspaceAccess access, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = unreadOnly
                ? notifications.findByWorkspaceIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        access.workspaceId(), access.userId(), pageable)
                : notifications.findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(
                        access.workspaceId(), access.userId(), pageable);
        return PageResponse.of(result, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(WorkspaceAccess access) {
        return notifications.countByWorkspaceIdAndUserIdAndReadAtIsNull(access.workspaceId(), access.userId());
    }

    @Transactional
    public void markRead(WorkspaceAccess access, UUID notificationId) {
        notifications.findByIdAndWorkspaceIdAndUserId(notificationId, access.workspaceId(), access.userId())
                .orElseThrow(ApiException::notFound)
                .markRead(Instant.now(clock));
    }

    @Transactional
    public void markAllRead(WorkspaceAccess access) {
        notifications.markAllRead(access.workspaceId(), access.userId(), Instant.now(clock));
    }

    /**
     * Tells the people involved that an order changed. The actor is never notified
     * about their own change.
     * - the assignee hears about changes made by others;
     * - when the assignee (e.g. an EMPLOYEE) changes the status, OWNER and MANAGER members hear about it.
     */
    @Transactional
    public void orderChanged(WorkspaceAccess access, CustomerOrder order, String change, Map<String, Object> details) {
        Set<UUID> recipients = new LinkedHashSet<>();
        User assignee = order.getAssignedUser();
        if (assignee != null) {
            recipients.add(assignee.getId());
            if (assignee.getId().equals(access.userId()) && "STATUS".equals(change)) {
                memberships.findByWorkspaceIdOrderByCreatedAtAsc(access.workspaceId()).stream()
                        .filter(m -> m.getRole().canManageRecords())
                        .map(WorkspaceMembership::getUser)
                        .map(User::getId)
                        .forEach(recipients::add);
            }
        }
        recipients.remove(access.userId());
        if (recipients.isEmpty()) {
            return;
        }

        String actorName = users.findById(access.userId()).map(User::getFullName).orElse("");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("title", order.getTitle());
        payload.put("change", change);
        payload.put("actor", actorName);
        payload.putAll(details);

        for (User user : users.findAllById(recipients)) {
            if (user.isNotifyOrderChanges()) {
                notifications.save(new Notification(access.workspaceId(), user.getId(),
                        Notification.Type.ORDER_CHANGED, order.getId(), payload));
            }
        }
    }
}
