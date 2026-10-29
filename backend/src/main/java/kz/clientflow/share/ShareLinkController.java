package kz.clientflow.share;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import kz.clientflow.auth.CurrentUser;
import kz.clientflow.share.ShareLinkService.CreatedLink;
import kz.clientflow.share.ShareLinkService.LinkStatus;
import kz.clientflow.share.ShareLinkService.PublicOrder;
import kz.clientflow.workspace.WorkspaceAccessService;

@RestController
public class ShareLinkController {

    private final ShareLinkService shareLinks;
    private final WorkspaceAccessService accessService;

    public ShareLinkController(ShareLinkService shareLinks, WorkspaceAccessService accessService) {
        this.shareLinks = shareLinks;
        this.accessService = accessService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/orders/{orderId}/share-link")
    LinkStatus status(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                      @AuthenticationPrincipal CurrentUser user) {
        return shareLinks.status(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/orders/{orderId}/share-link")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedLink create(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                       @AuthenticationPrincipal CurrentUser user) {
        return shareLinks.create(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/orders/{orderId}/share-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID workspaceId, @PathVariable UUID orderId,
                @AuthenticationPrincipal CurrentUser user) {
        shareLinks.revoke(accessService.requireMember(workspaceId, user.id()), orderId);
    }

    /** Public: no session needed. The token in the URL is the only credential. */
    @GetMapping("/api/v1/public/orders/{token}")
    ResponseEntity<PublicOrder> publicOrder(@PathVariable @Size(max = 100) String token, HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .body(shareLinks.publicView(token, request.getRemoteAddr()));
    }
}
