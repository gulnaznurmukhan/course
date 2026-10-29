package kz.clientflow.user;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kz.clientflow.auth.AccountService;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.auth.SessionService;
import kz.clientflow.auth.SessionService.SessionInfo;
import kz.clientflow.common.ApiException;
import kz.clientflow.user.dto.MeResponse;
import kz.clientflow.user.dto.ProfileRequests;
import kz.clientflow.workspace.WorkspaceAccessService;

/** The signed-in user's own profile, settings, sessions and account. */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;
    private final SessionService sessionService;
    private final WorkspaceAccessService accessService;

    public UserController(UserService userService, AccountService accountService, SessionService sessionService,
                          WorkspaceAccessService accessService) {
        this.userService = userService;
        this.accountService = accountService;
        this.sessionService = sessionService;
        this.accessService = accessService;
    }

    @GetMapping("/users/me")
    MeResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userService.getMe(currentUser.id());
    }

    @PatchMapping("/users/me")
    MeResponse updateProfile(@AuthenticationPrincipal CurrentUser user,
                             @Valid @RequestBody ProfileRequests.UpdateProfile body) {
        return userService.updateProfile(user.id(), body);
    }

    @PutMapping("/users/me/notification-settings")
    MeResponse notificationSettings(@AuthenticationPrincipal CurrentUser user,
                                    @Valid @RequestBody ProfileRequests.NotificationSettingsRequest body) {
        return userService.updateNotificationSettings(user.id(), body);
    }

    @PostMapping("/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal CurrentUser user,
                        @Valid @RequestBody ProfileRequests.ChangePassword body, HttpServletRequest request) {
        accountService.changePassword(user.id(), body.currentPassword(), body.newPassword(),
                request.getSession().getId());
        request.changeSessionId();
    }

    // ------------------------------------------------------------------ avatar

    @PutMapping(path = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void uploadAvatar(@AuthenticationPrincipal CurrentUser user, @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.getSize() > UserService.MAX_AVATAR_BYTES) {
            throw ApiException.badRequest("AVATAR_TOO_LARGE");
        }
        userService.uploadAvatar(user.id(), file.getBytes());
    }

    @DeleteMapping("/users/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAvatar(@AuthenticationPrincipal CurrentUser user) {
        userService.deleteAvatar(user.id());
    }

    @GetMapping("/users/me/avatar")
    ResponseEntity<byte[]> myAvatar(@AuthenticationPrincipal CurrentUser user) {
        return avatarResponse(userService.getAvatar(user.id()));
    }

    /** Avatar of a colleague: both users must belong to the workspace. */
    @GetMapping("/workspaces/{workspaceId}/members/{userId}/avatar")
    ResponseEntity<byte[]> memberAvatar(@PathVariable UUID workspaceId, @PathVariable UUID userId,
                                        @AuthenticationPrincipal CurrentUser user) {
        accessService.requireMember(workspaceId, user.id());
        accessService.requireMember(workspaceId, userId);
        return avatarResponse(userService.getAvatar(userId));
    }

    private static ResponseEntity<byte[]> avatarResponse(UserAvatar avatar) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.getContentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header("Content-Security-Policy", "default-src 'none'")
                .body(avatar.getData());
    }

    // ------------------------------------------------------------------ sessions

    @GetMapping("/users/me/sessions")
    List<SessionInfo> sessions(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        return sessionService.list(user.id(), request.getSession().getId());
    }

    @DeleteMapping("/users/me/sessions/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(@AuthenticationPrincipal CurrentUser user, @PathVariable String key) {
        sessionService.revoke(user.id(), key);
    }

    @PostMapping("/users/me/sessions/revoke-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeOtherSessions(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        sessionService.revokeAllExcept(user.id(), request.getSession().getId());
    }

    // ------------------------------------------------------------------ account deletion

    @DeleteMapping("/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@AuthenticationPrincipal CurrentUser user,
                       @Valid @RequestBody ProfileRequests.DeleteAccount body, HttpServletRequest request) {
        accountService.deleteAccount(user.id(), body.password());
        sessionService.end(request);
    }
}
