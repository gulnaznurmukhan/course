package kz.clientflow.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.payment.PaymentState;

/** Request and response bodies of the orders API. */
public final class OrderDtos {

    public static final String CURRENCY_PATTERN = "KZT|RUB|USD|EUR";

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotNull UUID clientId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2)
            BigDecimal totalAmount,
            @NotNull @Pattern(regexp = CURRENCY_PATTERN) String currency,
            LocalDate deadline,
            UUID assignedUserId) {
    }

    /** "version" must match the current version, otherwise the change is rejected (409). */
    public record UpdateOrderRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2)
            BigDecimal totalAmount,
            @NotNull @Pattern(regexp = CURRENCY_PATTERN) String currency,
            LocalDate deadline,
            UUID assignedUserId,
            @NotNull Long version) {
    }

    public record ChangeStatusRequest(@NotNull OrderStatus status, @NotNull Long version) {
    }

    public record NoteRequest(@NotBlank @Size(max = 2000) String body) {
    }

    public record Ref(UUID id, String name) {
    }

    public record OrderResponse(
            UUID id,
            String orderNumber,
            String title,
            String description,
            BigDecimal totalAmount,
            String currency,
            OrderStatus status,
            LocalDate deadline,
            boolean overdue,
            Ref client,
            Ref assignedUser,
            boolean archived,
            Instant createdAt,
            Instant updatedAt,
            long version,
            /** Calculated from valid payments; never stored. */
            BigDecimal paidAmount,
            BigDecimal balance,
            PaymentState paymentState,
            /** What the current user may do; the backend checks these again on every request. */
            List<OrderStatus> allowedTransitions,
            boolean canEdit) {
    }

    public record NoteResponse(UUID id, String body, Ref author, Instant createdAt) {
    }

    /** One Kanban column. */
    public record BoardColumn(OrderStatus status, List<OrderResponse> items, long total) {
    }

    public enum ArchiveFilter {
        ACTIVE, ARCHIVED, ALL
    }

    public enum SortField {
        CREATED_AT("createdAt"), UPDATED_AT("updatedAt"), DEADLINE("deadline"),
        TOTAL_AMOUNT("totalAmount"), ORDER_NUMBER("orderNumber");

        private final String property;

        SortField(String property) {
            this.property = property;
        }

        public String property() {
            return property;
        }
    }

    /** Validated list filters. Null means "no filter". */
    public record OrderFilter(
            String search,
            ArchiveFilter archive,
            OrderStatus status,
            UUID clientId,
            UUID assignedUserId,
            Boolean overdue,
            PaymentState paymentState) {
    }
}
