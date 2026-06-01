package com.kemaless.core.auth.repository;

import com.kemaless.core.model.RealmMiniApp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealmMiniAppAuthRepository extends JpaRepository<RealmMiniApp, UUID> {

    Optional<RealmMiniApp> findByRealmCodeAndMiniAppCode(String realmCode, String miniAppCode);
}
