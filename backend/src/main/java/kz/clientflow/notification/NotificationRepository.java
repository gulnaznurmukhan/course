package kz.clientflow.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Lookups always include both the user and the workspace. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID userId,
                                                                     Pageable pageable);

    Page<Notification> findByWorkspaceIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID workspaceId, UUID userId,
                                                                                     Pageable pageable);

    long countByWorkspaceIdAndUserIdAndReadAtIsNull(UUID workspaceId, UUID userId);

    Optional<Notification> findByIdAndWorkspaceIdAndUserId(UUID id, UUID workspaceId, UUID userId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.workspaceId = :workspaceId and n.userId = :userId and n.readAt is null""")
    int markAllRead(UUID workspaceId, UUID userId, Instant now);

    @Modifying
    @Query("delete from Notification n where n.workspaceId = :workspaceId and n.userId = :userId")
    int deleteForMember(UUID workspaceId, UUID userId);
}
