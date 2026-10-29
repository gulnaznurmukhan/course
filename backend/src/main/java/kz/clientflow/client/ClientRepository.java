package kz.clientflow.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Every lookup includes the workspace id, so records of other workspaces are never returned. */
public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    Optional<Client> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select c from Client c where c.workspaceId = :workspaceId and lower(c.email) = :email")
    List<Client> findByEmail(UUID workspaceId, String email, Limit limit);

    /**
     * Compares the last 10 digits only, so "+7 (701) 123-45-67", "87011234567"
     * and "7011234567" are recognised as the same number.
     */
    @Query(value = """
            SELECT * FROM clients
            WHERE workspace_id = :workspaceId
              AND right(regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g'), 10) = right(:digits, 10)
            LIMIT 5""", nativeQuery = true)
    List<Client> findByPhoneDigits(UUID workspaceId, String digits);
}
