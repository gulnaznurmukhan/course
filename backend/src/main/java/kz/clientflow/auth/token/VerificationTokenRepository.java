package kz.clientflow.auth.token;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    /** Locked so that two parallel requests cannot use the same token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /** Invalidates all still-unused tokens of a user (e.g. when a new one is sent). */
    @Modifying
    @Query("update VerificationToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int invalidateAllForUser(UUID userId, Instant now);
}
