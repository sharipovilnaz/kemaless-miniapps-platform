package com.kemaless.core.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.AccessContext;
import com.kemaless.core.auth.dto.CreateLaunchTicketCommand;
import com.kemaless.core.auth.dto.api.LaunchTicketResponse;
import com.kemaless.core.auth.entity.LaunchTicket;
import com.kemaless.core.enums.ClientStatus;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.auth.repository.LaunchTicketRepository;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmClient;
import com.kemaless.core.repository.RealmClientRepository;
import com.kemaless.core.repository.RealmRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LaunchTicketService {

    private static final int TICKET_BYTES = 64;

    private final PlatformAuthProperties properties;
    private final SecureRandom secureRandom;
    private final ObjectMapper objectMapper;
    private final TokenHashService tokenHashService;
    private final RealmRepository realmRepository;
    private final RealmClientRepository realmClientRepository;
    private final LaunchTicketRepository launchTicketRepository;

    @Transactional
    public LaunchTicketResponse createTicket(CreateLaunchTicketCommand command) {
        Realm realm = realmRepository.findByCode(command.realmCode())
                .orElseThrow(() -> new AuthException("Realm not found"));
        if (realm.getStatus() != RealmStatus.ACTIVE) {
            throw new AuthException("Realm disabled");
        }
        RealmClient client = realmClientRepository.findByRealmCodeAndClientId(command.realmCode(), command.clientId())
                .orElseThrow(() -> new AuthException("Client not found"));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new AuthException("Client disabled");
        }

        String rawTicket = generateToken();
        Instant expiresAt = Instant.now().plusSeconds(properties.launchTicketTtlSeconds());
        AccessContext accessContext = command.accessContext();

        LaunchTicket ticket = new LaunchTicket();
        ticket.setRealm(realm);
        ticket.setClient(client);
        ticket.setRealmMiniAppId(command.realmMiniAppId());
        ticket.setMiniAppCode(command.miniAppCode());
        ticket.setExternalUserId(accessContext.externalUserId());
        ticket.setEmail(accessContext.email());
        ticket.setRealmRoles(toJson(accessContext.realmRoles()));
        ticket.setTicketHash(tokenHashService.hash(rawTicket));
        ticket.setUserContext(toJson(accessContext));
        ticket.setLaunchContext(toJson(command.launchContext()));
        ticket.setExpiresAt(expiresAt);
        launchTicketRepository.save(ticket);

        return new LaunchTicketResponse(rawTicket, expiresAt);
    }

    private String generateToken() {
        byte[] bytes = new byte[TICKET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toJson(Object value) {
        Object safeValue = value == null ? Map.of() : value;
        try {
            return objectMapper.writeValueAsString(safeValue);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize launch context", exception);
        }
    }
}
