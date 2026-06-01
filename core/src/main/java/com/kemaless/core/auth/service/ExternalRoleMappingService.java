package com.kemaless.core.auth.service;

import com.kemaless.core.auth.dto.AccessContext;
import com.kemaless.core.auth.dto.LaunchUserContext;
import com.kemaless.core.auth.exception.AuthException;
import com.kemaless.core.model.ExternalRoleMapping;
import com.kemaless.core.model.RealmRole;
import com.kemaless.core.repository.ExternalRoleMappingRepository;
import com.kemaless.core.repository.RealmRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExternalRoleMappingService {

    private final RealmRepository realmRepository;
    private final ExternalRoleMappingRepository externalRoleMappingRepository;

    @Transactional(readOnly = true)
    public AccessContext map(String realmCode, LaunchUserContext launchUserContext) {
        realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new AuthException("Realm not found"));
        List<String> externalRoles = safeList(launchUserContext.externalRoles());
        Set<String> mappedRoles = new LinkedHashSet<>();
        if (!externalRoles.isEmpty()) {
            externalRoleMappingRepository.findByRealmCodeAndExternalRoleIn(realmCode, externalRoles).stream()
                    .map(ExternalRoleMapping::getRealmRole)
                    .map(RealmRole::getCode)
                    .forEach(mappedRoles::add);
        }
        return new AccessContext(
                launchUserContext.externalUserId(),
                launchUserContext.email(),
                List.copyOf(mappedRoles),
                safeMap(launchUserContext.attributes())
        );
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static Map<String, Object> safeMap(Map<String, Object> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }
}
