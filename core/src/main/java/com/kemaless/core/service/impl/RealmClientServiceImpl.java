package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.dto.request.CreateRealmClientRequest;
import com.kemaless.core.dto.response.CreateRealmClientResponse;
import com.kemaless.core.dto.response.RealmClientResponse;
import com.kemaless.core.dto.response.RotateSecretResponse;
import com.kemaless.core.exception.error.RealmClientAlreadyExistException;
import com.kemaless.core.exception.error.RealmClientNotFoundException;
import com.kemaless.core.exception.error.RealmNotFoundException;
import com.kemaless.core.enums.ClientStatus;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmClient;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.repository.RealmClientRepository;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.service.interfaces.AuthModuleService;
import com.kemaless.core.service.interfaces.RealmClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealmClientServiceImpl implements RealmClientService {

    private final RealmClientRepository clientRepository;
    private final RealmRepository realmRepository;
    private final AuthModuleService authModuleService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<RealmClientResponse> findAllByRealm(String realmCode) {
        log.debug("Fetching all clients for realm: {}", realmCode);

        return clientRepository.findAllByRealmCode(realmCode).stream()
                .map(client -> RealmClientResponse.builder()
                        .clientId(client.getClientId())
                        .status(client.getStatus())
                        .createdAt(client.getCreatedAt())
                        .updatedAt(client.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CreateRealmClientResponse create(String realmCode, CreateRealmClientRequest request) {
        log.info("Attempting to create new client '{}' for realm '{}'", request.clientId(), realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> {
                    log.warn("Realm not found: {}", realmCode);
                    return new RealmNotFoundException(realmCode);
                });

        if (realm.getStatus() != RealmStatus.ACTIVE) {
            log.warn("Cannot create client. Realm '{}' is not ACTIVE (current status: {})", realmCode, realm.getStatus());
            throw new IllegalStateException("Cannot create client for non-active realm");
        }

        if (clientRepository.existsByClientId(request.clientId())) {
            log.warn("Client ID '{}' already exists in the system", request.clientId());
            throw new RealmClientAlreadyExistException(request.clientId());
        }

        var credentials = authModuleService.generateSecret();
        log.debug("Generated new secret for client '{}'", request.clientId());

        RealmClient client = RealmClient.builder()
                .realm(realm)
                .clientId(request.clientId())
                .clientSecretHash(credentials.hashedSecret())
                .status(ClientStatus.ACTIVE)
                .build();

        clientRepository.save(client);
        auditService.write(realmCode, AuditAction.CLIENT_CREATED.name(), "REALM_CLIENT", client.getClientId());
        log.debug("Client '{}' saved to database with ID: {}", client.getClientId(), client.getId());


        log.info("Successfully created client '{}' for realm '{}'", client.getClientId(), realmCode);

        return CreateRealmClientResponse.builder()
                .clientId(client.getClientId())
                .clientSecret(credentials.rawSecret())
                .build();
    }

    @Override
    @Transactional
    public RotateSecretResponse rotateSecret(String realmCode, String clientId) {
        log.info("Attempting to rotate secret for client '{}' in realm '{}'", clientId, realmCode);

        RealmClient client = getClientOrThrow(realmCode, clientId);

        var credentials = authModuleService.generateSecret();
        client.setClientSecretHash(credentials.hashedSecret());

        clientRepository.save(client);
        auditService.write(realmCode, AuditAction.CLIENT_SECRET_ROTATED.name(), "REALM_CLIENT", client.getClientId());
        log.debug("New secret hash saved for client '{}'", clientId);

        log.info("Successfully rotated secret for client '{}'", clientId);
        return RotateSecretResponse.builder()
                .clientId(client.getClientId())
                .clientSecret(credentials.rawSecret())
                .build();
    }

    @Override
    @Transactional
    public void changeStatus(String realmCode, String clientId, boolean active) {
        log.info("Changing status for client '{}' in realm '{}' to active: {}", clientId, realmCode, active);

        RealmClient client = getClientOrThrow(realmCode, clientId);

        ClientStatus newStatus = active ? ClientStatus.ACTIVE : ClientStatus.BLOCKED;
        client.setStatus(newStatus);

        clientRepository.save(client);

        AuditAction event = active ? AuditAction.CLIENT_UNBLOCKED : AuditAction.CLIENT_BLOCKED;
        auditService.write(realmCode, event.name(), "REALM_CLIENT", client.getClientId());
        log.info("Successfully changed status for client '{}' to {}", clientId, newStatus);
    }

    private RealmClient getClientOrThrow(String realmCode, String clientId) {
        return clientRepository.findByRealmCodeAndClientId(realmCode, clientId)
                .orElseThrow(() -> {
                    log.warn("Client '{}' not found in realm '{}'", clientId, realmCode);
                    return new RealmClientNotFoundException(clientId);
                });
    }
}
