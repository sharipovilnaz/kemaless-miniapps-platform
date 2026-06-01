package com.kemaless.core.repository;

import com.kemaless.core.model.RealmClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealmClientRepository extends JpaRepository<RealmClient, UUID> {

    List<RealmClient> findByRealm_Code(String realmCode);

    List<RealmClient> findAllByRealmCode(String realmCode);

    boolean existsByClientId(String clientId);

    Optional<RealmClient> findByRealmCodeAndClientId(String realmCode, String clientId);
}
