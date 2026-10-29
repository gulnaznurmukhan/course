package kz.clientflow.common;

import java.time.Instant;
import java.util.List;

/**
 * Consistent JSON error body returned by every endpoint.
 * "code" is a stable machine-readable key that the frontend translates.
 */
public record ApiError(int status, String code, List<FieldError> fieldErrors, Instant timestamp) {

    public record FieldError(String field, String code) {
    }

    public static ApiError of(int status, String code) {
        return new ApiError(status, code, List.of(), Instant.now());
    }

    public static ApiError withFields(int status, String code, List<FieldError> fieldErrors) {
        return new ApiError(status, code, fieldErrors, Instant.now());
    }
}
