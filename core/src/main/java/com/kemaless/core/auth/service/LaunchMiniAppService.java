package com.kemaless.core.auth.service;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.domain.event.AuditEvent;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.auth.dto.AccessContext;
import com.kemaless.core.auth.dto.AccessDecision;
import com.kemaless.core.auth.dto.CreateLaunchTicketCommand;
import com.kemaless.core.auth.dto.LaunchUserContext;
import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
import com.kemaless.core.auth.dto.api.LaunchMiniAppRequest;
import com.kemaless.core.auth.dto.api.LaunchMiniAppResponse;
import com.kemaless.core.auth.dto.api.LaunchTicketResponse;
import com.kemaless.core.auth.enums.AccessDecisionStatus;
import com.kemaless.core.auth.enums.AuthErrorCode;
import com.kemaless.core.auth.exception.MiniappAccessDeniedException;
import com.kemaless.core.auth.exception.ServiceException;
import com.kemaless.core.auth.repository.RealmMiniAppAuthRepository;
import com.kemaless.core.model.RealmMiniApp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class LaunchMiniAppService {

    private final RealmMiniAppAuthRepository realmMiniAppRepository;
    private final ExternalRoleMappingService externalRoleMappingService;
    private final AccessControlService accessControlService;
    private final LaunchTicketService launchTicketService;
    private final AuditService auditService;

    @Transactional
    public LaunchMiniAppResponse launch(
            String realmCode,
            String miniAppCode,
            RealmClientJwtPrincipal clientPrincipal,
            LaunchMiniAppRequest request
    ) {
        RealmMiniApp miniApp = realmMiniAppRepository.findByRealmCodeAndMiniAppCode(realmCode, miniAppCode)
                .orElseThrow(() -> new ServiceException(AuthErrorCode.MINIAPP_NOT_FOUND.name(), 404));
        AccessContext accessContext = mapAccessContext(realmCode, request.user());
        AccessDecision decision = accessControlService.evaluate(miniApp, miniApp.getAccessPolicy(), accessContext);
        if (decision.status() != AccessDecisionStatus.ALLOW) {
            auditService.write(AuditEvent.builder()
                    .actorType("REALM_CLIENT")
                    .actorId(clientPrincipal.clientId())
                    .realmCode(realmCode)
                    .action(AuditAction.LAUNCH_DENIED.name())
                    .resourceType("REALM_MINIAPP")
                    .resourceId(miniAppCode)
                    .status(decision.reason().name())
                    .metadata(Map.of(
                            "miniAppCode", miniAppCode,
                            "decision", decision.status().name(),
                            "reason", decision.reason().name()
                    ))
                    .build());
            throw new MiniappAccessDeniedException(decision.reason().name());
        }
        LaunchTicketResponse ticket = launchTicketService.createTicket(new CreateLaunchTicketCommand(
                realmCode,
                clientPrincipal.clientId(),
                miniApp.getId(),
                miniAppCode,
                accessContext,
                Map.of(
                        "device", request.device() == null ? Map.of() : request.device(),
                        "context", request.context() == null ? Map.of() : request.context()
                )
        ));
        auditService.write(AuditEvent.builder()
                .actorType("REALM_CLIENT")
                .actorId(clientPrincipal.clientId())
                .realmCode(realmCode)
                .action(AuditAction.LAUNCH_SUCCESS.name())
                .resourceType("REALM_MINIAPP")
                .resourceId(miniAppCode)
                .status("SUCCESS")
                .metadata(Map.of(
                        "miniAppCode", miniAppCode,
                        "ticketExpiresAt", ticket.expiresAt().toString()
                ))
                .build());
        return new LaunchMiniAppResponse(
                appendTicket(miniApp.getEntryUrl(), ticket.ticket()),
                Math.max(0, Duration.between(Instant.now(), ticket.expiresAt()).toSeconds())
        );
    }

    private AccessContext mapAccessContext(String realmCode, LaunchMiniAppRequest.User user) {
        if (user == null) {
            return new AccessContext(null, null, List.of(), Map.of());
        }
        return externalRoleMappingService.map(
                realmCode,
                new LaunchUserContext(
                        user.externalUserId(),
                        user.email(),
                        user.roles(),
                        user.attributes()
                )
        );
    }

    private String appendTicket(String entryUrl, String ticket) {
        return UriComponentsBuilder.fromUriString(entryUrl)
                .replaceQueryParam("ticket", ticket)
                .build()
                .toUriString();
    }
}
