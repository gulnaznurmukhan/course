package kz.clientflow.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One entry of an activity history. "action" is translated by the frontend. */
public record ActivityResponse(
        Long id,
        String action,
        ActivityLog.EntityType entityType,
        UUID entityId,
        Actor actor,
        Map<String, Object> metadata,
        Instant createdAt) {

    public record Actor(UUID id, String fullName) {
    }

    public static ActivityResponse from(ActivityLog log) {
        Actor actor = log.getActor() == null ? null
                : new Actor(log.getActor().getId(), log.getActor().getFullName());
        return new ActivityResponse(log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                actor, log.getMetadata(), log.getCreatedAt());
    }
}
