package com.kemaless.core.auth.repository;

import com.kemaless.core.model.MiniAppAccessPolicy;
import com.kemaless.core.model.RealmMiniApp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MiniAppAccessPolicyAuthRepository extends JpaRepository<MiniAppAccessPolicy, UUID> {

    Optional<MiniAppAccessPolicy> findByRealmMiniApp(RealmMiniApp realmMiniApp);
}
