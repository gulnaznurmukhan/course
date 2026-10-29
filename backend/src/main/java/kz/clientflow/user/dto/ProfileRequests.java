package kz.clientflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.clientflow.auth.dto.AuthRequests;
import kz.clientflow.user.Theme;

/** Request bodies of the profile and account API. */
public final class ProfileRequests {

    private ProfileRequests() {
    }

    /** Fields that are null are left unchanged. Email and verification cannot be changed here. */
    public record UpdateProfile(
            @Size(min = 1, max = 100) String fullName,
            @Pattern(regexp = "kk|ru|en") String preferredLanguage,
            Theme theme) {
    }

    public record NotificationSettingsRequest(
            @NotNull Boolean deadlines,
            @NotNull Boolean overdue,
            @NotNull Boolean orderChanges,
            @NotNull Boolean payments) {
    }

    public record ChangePassword(
            @NotBlank @Size(max = AuthRequests.PASSWORD_MAX) String currentPassword,
            @NotBlank @Size(min = AuthRequests.PASSWORD_MIN, max = AuthRequests.PASSWORD_MAX) String newPassword) {
    }

    public record DeleteAccount(@NotBlank @Size(max = AuthRequests.PASSWORD_MAX) String password) {
    }
}
