package kz.clientflow.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * In-app notification for one user in one workspace.
 * The text is not stored: the frontend builds it in the user's language from
 * the type and the payload (order number, title, amounts...).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type {
        DEADLINE_APPROACHING, ORDER_OVERDUE, ORDER_CHANGED, PAYMENT_REMINDER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Type type;

    @Column(updatable = false)
    private UUID orderId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    /** Same key = same reminder; the database rejects duplicates per user. */
    @Column(length = 150, updatable = false)
    private String dedupKey;

    private Instant readAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID workspaceId, UUID userId, Type type, UUID orderId, Map<String, Object> payload) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.type = type;
        this.orderId = orderId;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
