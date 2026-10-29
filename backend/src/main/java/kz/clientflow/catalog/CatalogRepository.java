package kz.clientflow.catalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CatalogRepository extends JpaRepository<CatalogItem, UUID>, JpaSpecificationExecutor<CatalogItem> {

    Optional<CatalogItem> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
