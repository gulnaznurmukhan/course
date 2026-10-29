package kz.clientflow.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Profile picture bytes (PNG, JPEG or WebP, at most 512 KB). */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {
    }

    public UserAvatar(UUID userId) {
        this.userId = userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replace(String contentType, byte[] data, Instant now) {
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = now;
    }
}
