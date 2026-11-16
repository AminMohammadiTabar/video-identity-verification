package com.example.videokyc.dms.config;

import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.service.DmsFileService;
import com.example.videokyc.dms.observability.audit.AuditEventCommand;
import com.example.videokyc.dms.observability.audit.AuditEventRecorder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** AUTH_SERVICE is limited to AUTH user files; the browser never gets this credential. */
@Component
public class DmsAccessPolicy {
    private final DmsFileService files;
    private final AuditEventRecorder audit;

    public DmsAccessPolicy(DmsFileService files, AuditEventRecorder audit) {
        this.files = files;
        this.audit = audit;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void requireService(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_AUTH_SERVICE".equals(authority.getAuthority()))) {
            audit.recordIndependent(AuditEventCommand.of("dms.file.access.denied", "ANONYMOUS", null,
                    null, null, "DMS_FILE", null, "DENIED", "AUTH_SERVICE_REQUIRED", null, null));
            throw new AccessDeniedException("DMS access denied");
        }
    }

    public void upload(Authentication authentication, String subsystem,
                       FileOwnerType ownerType, String ownerReference) {
        if (isAdmin(authentication)) return;
        requireService(authentication);
        try {
            if (!"AUTH".equals(subsystem) || ownerType != FileOwnerType.USER ||
                    !UUID.fromString(ownerReference).toString().equals(ownerReference)) {
                audit.recordIndependent(AuditEventCommand.of("dms.file.access.denied", "SERVICE",
                        authentication.getName(), null, null, "DMS_FILE", null, "DENIED",
                        "AUTH_SERVICE_SCOPE_VIOLATION", null, null));
                throw new AccessDeniedException("Service may only upload AUTH user files");
            }
        } catch (IllegalArgumentException ex) {
            audit.recordIndependent(AuditEventCommand.of("dms.file.access.denied", "SERVICE",
                    authentication.getName(), null, null, "DMS_FILE", null, "DENIED",
                    "AUTH_SERVICE_SCOPE_VIOLATION", null, null));
            throw new AccessDeniedException("Service may only upload AUTH user files");
        }
    }

    public void accessFile(Authentication authentication, UUID fileId) {
        if (isAdmin(authentication)) return;
        try {
            requireService(authentication);
            var metadata = files.metadataForAuthorization(fileId);
            if (!"AUTH".equals(metadata.subsystem()) || metadata.ownerType() != FileOwnerType.USER) {
                audit.recordIndependent(AuditEventCommand.of("dms.file.access.denied", "SERVICE",
                        authentication.getName(), null, null, "DMS_FILE", fileId.toString(), "DENIED",
                        "AUTH_SERVICE_SCOPE_VIOLATION", null, null));
                throw new AccessDeniedException("Service may only access AUTH user files");
            }
        } catch (AccessDeniedException denied) {
            throw denied;
        }
    }
}
