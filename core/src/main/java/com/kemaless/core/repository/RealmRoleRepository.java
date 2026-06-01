package com.kemaless.core.repository;

import com.kemaless.core.model.RealmRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealmRoleRepository extends JpaRepository<RealmRole, UUID> {

    boolean existsByRealmCodeAndCode(String realmCode, String code);

    Optional<RealmRole> findByRealmCodeAndCode(String realmCode, String code);

    List<RealmRole> findAllByRealmCode(String realmCode);
}
