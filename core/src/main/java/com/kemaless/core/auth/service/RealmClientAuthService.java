package com.kemaless.core.auth.service;

import com.kemaless.core.auth.config.PlatformAuthProperties;
import com.kemaless.core.auth.dto.api.HostServiceTokenResponse;
import com.kemaless.core.auth.dto.api.RealmClientTokenRequest;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.enums.ClientStatus;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmClient;
import com.kemaless.core.repository.RealmClientRepository;
import com.kemaless.core.repository.RealmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RealmClientAuthService {

    private static final String INVALID_CLIENT_MESSAGE = "Invalid client credentials";

    private final PlatformAuthProperties properties;
    private final RealmRepository realmRepository;
    private final RealmClientRepository realmClientRepository;
    private final RealmClientSecretService realmClientSecretService;
    private final HostServiceJwtService hostServiceJwtService;

    @Transactional(readOnly = true)
    public HostServiceTokenResponse issueToken(String realmCode, RealmClientTokenRequest request) {
        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new AuthException(INVALID_CLIENT_MESSAGE));
        if (realm.getStatus() != RealmStatus.ACTIVE) {
            throw new AuthException(INVALID_CLIENT_MESSAGE);
        }
        RealmClient client = realmClientRepository.findByRealmCodeAndClientId(realmCode, request.clientId())
                .orElseThrow(() -> new AuthException(INVALID_CLIENT_MESSAGE));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new AuthException(INVALID_CLIENT_MESSAGE);
        }
        if (!realmClientSecretService.matches(request.clientSecret(), client.getClientSecretHash())) {
            throw new AuthException(INVALID_CLIENT_MESSAGE);
        }
        String accessToken = hostServiceJwtService.createAccessToken(client);
        return HostServiceTokenResponse.bearer(accessToken, properties.accessTokenTtlSeconds());
    }
}
