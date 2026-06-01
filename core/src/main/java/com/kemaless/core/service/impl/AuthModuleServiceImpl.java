package com.kemaless.core.service.impl;

import com.kemaless.core.auth.service.RealmClientSecretService;
import com.kemaless.core.enums.ClientStatus;
import com.kemaless.core.exception.error.RealmClientAlreadyExistException;
import com.kemaless.core.exception.error.RealmNotFoundException;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmClient;
import com.kemaless.core.repository.RealmClientRepository;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.service.auth.DefaultRealmClientCredentials;
import com.kemaless.core.service.auth.GeneratedClientSecret;
import com.kemaless.core.service.interfaces.AuthModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthModuleServiceImpl implements AuthModuleService {

    private final RealmRepository realmRepository;
    private final RealmClientRepository realmClientRepository;
    private final RealmClientSecretService realmClientSecretService;

    @Override
    public GeneratedClientSecret generateSecret() {
        String raw = realmClientSecretService.generateSecret();
        return new GeneratedClientSecret(raw, realmClientSecretService.hashSecret(raw));
    }

    @Override
    @Transactional
    public DefaultRealmClientCredentials createDefaultClient(String realmCode) {
        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new RealmNotFoundException(realmCode));

        String clientId = "default-" + realm.getCode();
        if (realmClientRepository.existsByClientId(clientId)) {
            throw new RealmClientAlreadyExistException(clientId);
        }

        GeneratedClientSecret secret = generateSecret();
        RealmClient client = RealmClient.builder()
                .realm(realm)
                .clientId(clientId)
                .clientSecretHash(secret.hashedSecret())
                .status(ClientStatus.ACTIVE)
                .build();
        realmClientRepository.save(client);

        return new DefaultRealmClientCredentials(clientId, secret.rawSecret());
    }

    @Override
    @Transactional
    public void disableAllClients(String realmCode) {
        List<RealmClient> clients = realmClientRepository.findByRealm_Code(realmCode);
        for (RealmClient client : clients) {
            client.setStatus(ClientStatus.BLOCKED);
        }
        realmClientRepository.saveAll(clients);
    }
}
