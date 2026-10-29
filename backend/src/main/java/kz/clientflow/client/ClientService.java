package kz.clientflow.client;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.client.dto.ClientDtos.ClientDetailsResponse;
import kz.clientflow.client.dto.ClientDtos.ClientRequest;
import kz.clientflow.client.dto.ClientDtos.ClientResponse;
import kz.clientflow.client.dto.ClientDtos.DuplicateClient;
import kz.clientflow.client.dto.ClientDtos.SortField;
import kz.clientflow.client.dto.ClientDtos.StatusFilter;
import kz.clientflow.common.ApiException;
import kz.clientflow.common.PageResponse;
import kz.clientflow.common.SearchText;
import kz.clientflow.order.OrderRepository;
import kz.clientflow.order.OrderStatus;
import kz.clientflow.workspace.WorkspaceAccess;

/**
 * Business rules for clients. All members can view clients;
 * only OWNER and MANAGER can create, edit, archive and restore them.
 * A client with active orders cannot be archived.
 */
@Service
public class ClientService {

    private final ClientRepository clients;
    private final OrderRepository orders;
    private final ActivityService activity;
    private final Clock clock;

    public ClientService(ClientRepository clients, OrderRepository orders, ActivityService activity, Clock clock) {
        this.clients = clients;
        this.orders = orders;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> list(WorkspaceAccess access, String search, StatusFilter status,
                                             SortField sort, Sort.Direction direction, int page, int size) {
        Specification<Client> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("workspaceId"), access.workspaceId()));
            if (status == StatusFilter.ACTIVE) {
                where.add(cb.isNull(root.get("archivedAt")));
            } else if (status == StatusFilter.ARCHIVED) {
                where.add(cb.isNotNull(root.get("archivedAt")));
            }
            if (!SearchText.isBlank(search)) {
                String pattern = SearchText.containsPattern(search);
                where.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("phone")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("company")), pattern, SearchText.ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        // Names are sorted without regard to case: "alpha" comes before "Bravo"
        Sort.Order order = new Sort.Order(direction, sort.property());
        if (sort == SortField.NAME) {
            order = order.ignoreCase();
        }
        var pageable = PageRequest.of(page, size, Sort.by(order).and(Sort.by("id")));
        return PageResponse.of(clients.findAll(spec, pageable), ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientDetailsResponse get(WorkspaceAccess access, UUID clientId) {
        Client client = load(access, clientId);
        long total = orders.countByWorkspaceIdAndClientId(access.workspaceId(), clientId);
        long active = orders.countByWorkspaceIdAndClientIdAndStatusIn(access.workspaceId(), clientId,
                OrderStatus.activeStatuses());
        return new ClientDetailsResponse(ClientResponse.from(client), total, active);
    }

    /**
     * Existing clients with the same email or phone (digits only), to warn the user
     * before a duplicate contact is created. Never blocks saving.
     */
    @Transactional(readOnly = true)
    public List<DuplicateClient> findDuplicates(WorkspaceAccess access, String email, String phone, UUID excludeId) {
        Map<UUID, Client> found = new LinkedHashMap<>();
        if (!SearchText.isBlank(email)) {
            clients.findByEmail(access.workspaceId(), email.trim().toLowerCase(Locale.ROOT), Limit.of(5))
                    .forEach(c -> found.put(c.getId(), c));
        }
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 6) {
            clients.findByPhoneDigits(access.workspaceId(), digits).forEach(c -> found.put(c.getId(), c));
        }
        if (excludeId != null) {
            found.remove(excludeId);
        }
        return found.values().stream().map(DuplicateClient::from).toList();
    }

    @Transactional
    public ClientResponse create(WorkspaceAccess access, ClientRequest request) {
        requireManager(access);
        Client client = new Client(access.workspaceId());
        apply(client, request);
        clients.save(client);
        activity.record(access, "CLIENT_CREATED", ActivityLog.EntityType.CLIENT, client.getId(),
                Map.of("name", client.getName()));
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse update(WorkspaceAccess access, UUID clientId, ClientRequest request) {
        requireManager(access);
        Client client = load(access, clientId);
        apply(client, request);
        clients.saveAndFlush(client);
        activity.record(access, "CLIENT_UPDATED", ActivityLog.EntityType.CLIENT, client.getId(),
                Map.of("name", client.getName()));
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse archive(WorkspaceAccess access, UUID clientId) {
        requireManager(access);
        Client client = load(access, clientId);
        long active = orders.countByWorkspaceIdAndClientIdAndStatusIn(access.workspaceId(), clientId,
                OrderStatus.activeStatuses());
        if (active > 0) {
            throw ApiException.conflict("CLIENT_HAS_ACTIVE_ORDERS");
        }
        if (!client.isArchived()) {
            client.archive(Instant.now(clock));
            clients.saveAndFlush(client);
            activity.record(access, "CLIENT_ARCHIVED", ActivityLog.EntityType.CLIENT, client.getId(), Map.of());
        }
        return ClientResponse.from(client);
    }

    @Transactional
    public ClientResponse restore(WorkspaceAccess access, UUID clientId) {
        requireManager(access);
        Client client = load(access, clientId);
        if (client.isArchived()) {
            client.restore();
            clients.saveAndFlush(client);
            activity.record(access, "CLIENT_RESTORED", ActivityLog.EntityType.CLIENT, client.getId(), Map.of());
        }
        return ClientResponse.from(client);
    }

    private Client load(WorkspaceAccess access, UUID clientId) {
        return clients.findByIdAndWorkspaceId(clientId, access.workspaceId()).orElseThrow(ApiException::notFound);
    }

    /** Copies only allowlisted fields from the request (no mass assignment). */
    private static void apply(Client client, ClientRequest request) {
        client.setName(request.name().trim());
        client.setEmail(emailOrNull(request.email()));
        client.setPhone(blankToNull(request.phone()));
        client.setCompany(blankToNull(request.company()));
        client.setAddress(blankToNull(request.address()));
        client.setNotes(blankToNull(request.notes()));
    }

    private static void requireManager(WorkspaceAccess access) {
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
    }

    private static String emailOrNull(String email) {
        return SearchText.isBlank(email) ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return SearchText.isBlank(value) ? null : value.trim();
    }
}
