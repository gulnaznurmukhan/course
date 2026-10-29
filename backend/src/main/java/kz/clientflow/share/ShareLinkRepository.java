package kz.clientflow.share;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHash(String tokenHash);

    List<ShareLink> findByWorkspaceIdAndOrderIdAndRevokedAtIsNull(UUID workspaceId, UUID orderId);
}
