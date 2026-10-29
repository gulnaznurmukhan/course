package kz.clientflow.notification;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.common.PageResponse;
import kz.clientflow.notification.NotificationService.NotificationResponse;
import kz.clientflow.workspace.WorkspaceAccessService;

/** Notifications of the signed-in user in one workspace. */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final WorkspaceAccessService accessService;

    public NotificationController(NotificationService notificationService, WorkspaceAccessService accessService) {
        this.notificationService = notificationService;
        this.accessService = accessService;
    }

    @GetMapping
    PageResponse<NotificationResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(defaultValue = "false") boolean unreadOnly,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.list(accessService.requireMember(workspaceId, user.id()), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user) {
        return Map.of("count", notificationService.unreadCount(accessService.requireMember(workspaceId, user.id())));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID workspaceId, @PathVariable UUID notificationId,
                  @AuthenticationPrincipal CurrentUser user) {
        notificationService.markRead(accessService.requireMember(workspaceId, user.id()), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user) {
        notificationService.markAllRead(accessService.requireMember(workspaceId, user.id()));
    }
}
