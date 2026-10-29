package kz.clientflow.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kz.clientflow.common.ApiException;
import kz.clientflow.user.dto.MeResponse;
import kz.clientflow.user.dto.ProfileRequests;
import kz.clientflow.workspace.WorkspaceMembershipRepository;

/** Profile, preferences and avatar of the signed-in user. */
@Service
public class UserService {

    public static final int MAX_AVATAR_BYTES = 512 * 1024;

    private final UserRepository users;
    private final WorkspaceMembershipRepository memberships;
    private final UserAvatarRepository avatars;
    private final Clock clock;

    public UserService(UserRepository users, WorkspaceMembershipRepository memberships, UserAvatarRepository avatars,
                       Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.avatars = avatars;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        User user = load(userId);
        var workspaces = memberships.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(m -> new MeResponse.WorkspaceSummary(m.getWorkspace().getId(), m.getWorkspace().getName(),
                        m.getRole()))
                .toList();
        var notifications = new MeResponse.NotificationSettings(user.isNotifyDeadlines(), user.isNotifyOverdue(),
                user.isNotifyOrderChanges(), user.isNotifyPayments());
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPreferredLanguage(),
                user.getTheme(), notifications, avatars.findUpdatedAt(userId).orElse(null), workspaces);
    }

    @Transactional
    public MeResponse updateProfile(UUID userId, ProfileRequests.UpdateProfile request) {
        User user = load(userId);
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw ApiException.badRequest("VALIDATION_FAILED");
            }
            user.setFullName(request.fullName().trim());
        }
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        if (request.theme() != null) {
            user.setTheme(request.theme());
        }
        return getMe(userId);
    }

    @Transactional
    public MeResponse updateNotificationSettings(UUID userId, ProfileRequests.NotificationSettingsRequest request) {
        User user = load(userId);
        user.setNotifyDeadlines(request.deadlines());
        user.setNotifyOverdue(request.overdue());
        user.setNotifyOrderChanges(request.orderChanges());
        user.setNotifyPayments(request.payments());
        return getMe(userId);
    }

    // ------------------------------------------------------------------ avatar

    /**
     * The image type is detected from the file's first bytes, never trusted from the
     * client. Only PNG, JPEG and WebP are accepted (no SVG: it can contain scripts).
     */
    @Transactional
    public void uploadAvatar(UUID userId, byte[] data) {
        if (data.length == 0 || data.length > MAX_AVATAR_BYTES) {
            throw ApiException.badRequest("AVATAR_TOO_LARGE");
        }
        String type = detectImageType(data);
        if (type == null) {
            throw ApiException.badRequest("AVATAR_INVALID_TYPE");
        }
        UserAvatar avatar = avatars.findById(userId).orElseGet(() -> new UserAvatar(userId));
        avatar.replace(type, data, Instant.now(clock));
        avatars.save(avatar);
    }

    @Transactional(readOnly = true)
    public UserAvatar getAvatar(UUID userId) {
        return avatars.findById(userId).orElseThrow(ApiException::notFound);
    }

    @Transactional
    public void deleteAvatar(UUID userId) {
        avatars.deleteById(userId);
    }

    static String detectImageType(byte[] d) {
        if (startsWith(d, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(d, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (d.length >= 12 && startsWith(d, 'R', 'I', 'F', 'F')
                && Arrays.equals(Arrays.copyOfRange(d, 8, 12), new byte[] {'W', 'E', 'B', 'P'})) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
    }
}
