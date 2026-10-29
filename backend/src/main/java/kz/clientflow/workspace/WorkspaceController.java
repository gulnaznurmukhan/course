package kz.clientflow.workspace;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import kz.clientflow.activity.ActivityResponse;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.common.PageResponse;
import kz.clientflow.workspace.dto.WorkspaceDtos.ChangeRoleRequest;
import kz.clientflow.workspace.dto.WorkspaceDtos.InviteRequest;
import kz.clientflow.workspace.dto.WorkspaceDtos.MembersResponse;
import kz.clientflow.workspace.dto.WorkspaceDtos.RenameRequest;
import kz.clientflow.workspace.dto.WorkspaceDtos.WorkspaceResponse;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceAccessService accessService;
    private final ActivityService activityService;

    public WorkspaceController(WorkspaceService workspaceService, WorkspaceAccessService accessService,
                               ActivityService activityService) {
        this.workspaceService = workspaceService;
        this.accessService = accessService;
        this.activityService = activityService;
    }

    @GetMapping
    WorkspaceResponse get(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user) {
        return workspaceService.get(accessService.requireMember(workspaceId, user.id()));
    }

    @PatchMapping
    WorkspaceResponse rename(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                             @Valid @RequestBody RenameRequest body) {
        return workspaceService.rename(accessService.requireMember(workspaceId, user.id()), body.name());
    }

    @GetMapping("/requisites")
    Requisites requisites(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user) {
        return workspaceService.requisites(accessService.requireMember(workspaceId, user.id()));
    }

    @PutMapping("/requisites")
    Requisites updateRequisites(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                @Valid @RequestBody Requisites body) {
        return workspaceService.updateRequisites(accessService.requireMember(workspaceId, user.id()), body);
    }

    @GetMapping("/members")
    MembersResponse members(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user) {
        return workspaceService.members(accessService.requireMember(workspaceId, user.id()));
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void invite(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                @Valid @RequestBody InviteRequest body) {
        workspaceService.invite(accessService.requireMember(workspaceId, user.id()), body.email(), body.role());
    }

    @PatchMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeRole(@PathVariable UUID workspaceId, @PathVariable UUID userId,
                    @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ChangeRoleRequest body) {
        workspaceService.changeRole(accessService.requireMember(workspaceId, user.id()), userId, body.role());
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable UUID workspaceId, @PathVariable UUID userId,
                      @AuthenticationPrincipal CurrentUser user) {
        workspaceService.removeMember(accessService.requireMember(workspaceId, user.id()), userId);
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelInvitation(@PathVariable UUID workspaceId, @PathVariable UUID invitationId,
                          @AuthenticationPrincipal CurrentUser user) {
        workspaceService.cancelInvitation(accessService.requireMember(workspaceId, user.id()), invitationId);
    }

    /** Recent activity of the whole workspace (OWNER and MANAGER). */
    @GetMapping("/activity")
    PageResponse<ActivityResponse> activity(@PathVariable UUID workspaceId, @AuthenticationPrincipal CurrentUser user,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        WorkspaceAccess access = accessService.requireRecordManager(workspaceId, user.id());
        return activityService.forWorkspace(access.workspaceId(), page, size);
    }
}
