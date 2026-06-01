package com.kemaless.core.auth.service;

import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
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
public class RealmClientTokenValidationService {

    private final RealmRepository realmRepository;
    private final RealmClientRepository realmClientRepository;

    @Transactional(readOnly = true)
    public void validateActiveClient(RealmClientJwtPrincipal principal) {
        Realm realm = realmRepository.findByCode(principal.realmCode())
                .orElseThrow(() -> new AuthException("Invalid host service token"));
        if (realm.getStatus() != RealmStatus.ACTIVE) {
            throw new AuthException("Invalid host service token");
        }
        RealmClient client = realmClientRepository.findByRealmCodeAndClientId(principal.realmCode(), principal.clientId())
                .orElseThrow(() -> new AuthException("Invalid host service token"));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new AuthException("Invalid host service token");
        }
    }
}
