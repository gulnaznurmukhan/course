package kz.clientflow.catalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.common.ApiException;
import kz.clientflow.common.PageResponse;
import kz.clientflow.common.SearchText;
import kz.clientflow.order.dto.OrderDtos;
import kz.clientflow.workspace.WorkspaceAccess;

/** Price list of the workspace. Everybody can view it; OWNER and MANAGER edit it. */
@Service
public class CatalogService {

    public record ServiceRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0.00") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2)
            BigDecimal price,
            @NotNull @Pattern(regexp = OrderDtos.CURRENCY_PATTERN) String currency) {
    }

    public record ServiceResponse(UUID id, String name, String description, String unit, BigDecimal price,
                                  String currency, boolean archived) {

        static ServiceResponse from(CatalogItem s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getUnit(), s.getPrice(),
                    s.getCurrency(), s.isArchived());
        }
    }

    public enum StatusFilter {
        ACTIVE, ARCHIVED, ALL
    }

    private final CatalogRepository services;
    private final ActivityService activity;
    private final Clock clock;

    public CatalogService(CatalogRepository services, ActivityService activity, Clock clock) {
        this.services = services;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> list(WorkspaceAccess access, String search, StatusFilter status,
                                              String currency, int page, int size) {
        Specification<CatalogItem> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("workspaceId"), access.workspaceId()));
            if (status == StatusFilter.ACTIVE) {
                where.add(cb.isNull(root.get("archivedAt")));
            } else if (status == StatusFilter.ARCHIVED) {
                where.add(cb.isNotNull(root.get("archivedAt")));
            }
            if (currency != null) {
                where.add(cb.equal(root.get("currency"), currency));
            }
            if (!SearchText.isBlank(search)) {
                where.add(cb.like(cb.lower(root.get("name")), SearchText.containsPattern(search), SearchText.ESCAPE));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")));
        return PageResponse.of(services.findAll(spec, pageable), ServiceResponse::from);
    }

    @Transactional
    public ServiceResponse create(WorkspaceAccess access, ServiceRequest request) {
        requireManager(access);
        CatalogItem item = new CatalogItem(access.workspaceId());
        apply(item, request);
        services.save(item);
        activity.record(access, "SERVICE_CREATED", ActivityLog.EntityType.WORKSPACE, item.getId(),
                Map.of("name", item.getName()));
        return ServiceResponse.from(item);
    }

    @Transactional
    public ServiceResponse update(WorkspaceAccess access, UUID id, ServiceRequest request) {
        requireManager(access);
        CatalogItem item = load(access, id);
        apply(item, request);
        services.saveAndFlush(item);
        return ServiceResponse.from(item);
    }

    @Transactional
    public ServiceResponse setArchived(WorkspaceAccess access, UUID id, boolean archived) {
        requireManager(access);
        CatalogItem item = load(access, id);
        if (archived) {
            item.archive(Instant.now(clock));
        } else {
            item.restore();
        }
        services.saveAndFlush(item);
        return ServiceResponse.from(item);
    }

    private CatalogItem load(WorkspaceAccess access, UUID id) {
        return services.findByIdAndWorkspaceId(id, access.workspaceId()).orElseThrow(ApiException::notFound);
    }

    private static void apply(CatalogItem item, ServiceRequest request) {
        item.setName(request.name().trim());
        item.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        item.setUnit(request.unit().trim());
        item.setPrice(request.price());
        item.setCurrency(request.currency());
    }

    private static void requireManager(WorkspaceAccess access) {
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
    }
}
