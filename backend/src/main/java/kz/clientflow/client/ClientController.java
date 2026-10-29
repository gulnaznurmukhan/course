package kz.clientflow.client;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.client.dto.ClientDtos.ClientDetailsResponse;
import kz.clientflow.client.dto.ClientDtos.ClientRequest;
import kz.clientflow.client.dto.ClientDtos.ClientResponse;
import kz.clientflow.client.dto.ClientDtos.DuplicateClient;
import kz.clientflow.client.dto.ClientDtos.SortField;
import kz.clientflow.client.dto.ClientDtos.StatusFilter;
import kz.clientflow.common.ApiException;
import kz.clientflow.common.PageResponse;
import kz.clientflow.workspace.WorkspaceAccessService;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientImportService importService;
    private final WorkspaceAccessService accessService;

    public ClientController(ClientService clientService, ClientImportService importService,
                            WorkspaceAccessService accessService) {
        this.clientService = clientService;
        this.importService = importService;
        this.accessService = accessService;
    }

    /** CSV import. dryRun=true only validates and returns the preview. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ClientImportService.ImportResult importCsv(@PathVariable UUID workspaceId,
                                               @AuthenticationPrincipal CurrentUser user,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(defaultValue = "true") boolean dryRun)
            throws IOException {
        if (file.getSize() > ClientImportService.MAX_BYTES) {
            throw ApiException.badRequest("IMPORT_FILE_SIZE");
        }
        return importService.importCsv(accessService.requireMember(workspaceId, user.id()), file.getBytes(), dryRun);
    }

    @GetMapping
    PageResponse<ClientResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                      @RequestParam(required = false) @Size(max = 100) String search,
                                      @RequestParam(defaultValue = "ACTIVE") StatusFilter status,
                                      @RequestParam(defaultValue = "NAME") SortField sort,
                                      @RequestParam(defaultValue = "ASC") Sort.Direction direction,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return clientService.list(accessService.requireMember(workspaceId, user.id()),
                search, status, sort, direction, page, size);
    }

    @GetMapping("/duplicates")
    List<DuplicateClient> duplicates(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                     @RequestParam(required = false) @Size(max = 254) String email,
                                     @RequestParam(required = false) @Size(max = 30) String phone,
                                     @RequestParam(required = false) UUID excludeId) {
        return clientService.findDuplicates(accessService.requireMember(workspaceId, user.id()),
                email, phone, excludeId);
    }

    @GetMapping("/{clientId}")
    ClientDetailsResponse get(@PathVariable UUID workspaceId, @PathVariable UUID clientId,
                              @AuthenticationPrincipal CurrentUser user) {
        return clientService.get(accessService.requireMember(workspaceId, user.id()), clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClientResponse create(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                          @Valid @RequestBody ClientRequest body) {
        return clientService.create(accessService.requireMember(workspaceId, user.id()), body);
    }

    @PutMapping("/{clientId}")
    ClientResponse update(@PathVariable UUID workspaceId, @PathVariable UUID clientId,
                          @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ClientRequest body) {
        return clientService.update(accessService.requireMember(workspaceId, user.id()), clientId, body);
    }

    @PostMapping("/{clientId}/archive")
    ClientResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID clientId,
                           @AuthenticationPrincipal CurrentUser user) {
        return clientService.archive(accessService.requireMember(workspaceId, user.id()), clientId);
    }

    @PostMapping("/{clientId}/restore")
    ClientResponse restore(@PathVariable UUID workspaceId, @PathVariable UUID clientId,
                           @AuthenticationPrincipal CurrentUser user) {
        return clientService.restore(accessService.requireMember(workspaceId, user.id()), clientId);
    }
}
