package kz.clientflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the /api/v1/auth endpoints. */
public final class AuthRequests {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;

    private AuthRequests() {
    }

    public record Register(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String password,
            @Pattern(regexp = "kk|ru|en") String language) {
    }

    public record Login(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = PASSWORD_MAX) String password) {
    }

    public record EmailOnly(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record TokenOnly(@NotBlank @Size(max = 100) String token) {
    }

    public record ResetPassword(
            @NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = PASSWORD_MIN, max = PASSWORD_MAX) String newPassword) {
    }
}
