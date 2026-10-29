package kz.clientflow.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderNoteRepository extends JpaRepository<OrderNote, UUID> {

    @EntityGraph(attributePaths = "author")
    List<OrderNote> findByWorkspaceIdAndOrderIdOrderByCreatedAtAsc(UUID workspaceId, UUID orderId);
}
