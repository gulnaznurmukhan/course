package kz.clientflow.catalog;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.catalog.CatalogService.ServiceRequest;
import kz.clientflow.catalog.CatalogService.ServiceResponse;
import kz.clientflow.catalog.CatalogService.StatusFilter;
import kz.clientflow.common.PageResponse;
import kz.clientflow.order.dto.OrderDtos;
import kz.clientflow.workspace.WorkspaceAccessService;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/services")
public class CatalogController {

    private final CatalogService catalogService;
    private final WorkspaceAccessService accessService;

    public CatalogController(CatalogService catalogService, WorkspaceAccessService accessService) {
        this.catalogService = catalogService;
        this.accessService = accessService;
    }

    @GetMapping
    PageResponse<ServiceResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                       @RequestParam(required = false) @Size(max = 100) String search,
                                       @RequestParam(defaultValue = "ACTIVE") StatusFilter status,
                                       @RequestParam(required = false) @Pattern(regexp = OrderDtos.CURRENCY_PATTERN)
                                       String currency,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return catalogService.list(accessService.requireMember(workspaceId, user.id()), search, status, currency,
                page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServiceResponse create(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                           @Valid @RequestBody ServiceRequest body) {
        return catalogService.create(accessService.requireMember(workspaceId, user.id()), body);
    }

    @PutMapping("/{serviceId}")
    ServiceResponse update(@PathVariable UUID workspaceId, @PathVariable UUID serviceId,
                           @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ServiceRequest body) {
        return catalogService.update(accessService.requireMember(workspaceId, user.id()), serviceId, body);
    }

    @PostMapping("/{serviceId}/archive")
    ServiceResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID serviceId,
                            @AuthenticationPrincipal CurrentUser user) {
        return catalogService.setArchived(accessService.requireMember(workspaceId, user.id()), serviceId, true);
    }

    @PostMapping("/{serviceId}/restore")
    ServiceResponse restore(@PathVariable UUID workspaceId, @PathVariable UUID serviceId,
                            @AuthenticationPrincipal CurrentUser user) {
        return catalogService.setArchived(accessService.requireMember(workspaceId, user.id()), serviceId, false);
    }
}
