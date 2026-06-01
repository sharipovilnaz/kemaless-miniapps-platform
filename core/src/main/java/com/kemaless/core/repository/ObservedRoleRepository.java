package com.kemaless.core.repository;

import com.kemaless.core.enums.RoleSource;
import com.kemaless.core.model.ObservedRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObservedRoleRepository extends JpaRepository<ObservedRole, UUID> {
    List<ObservedRole> findAllByRealmCode(String realmCode);
    Optional<ObservedRole> findByRealmCodeAndId(String realmCode, UUID id);
    Optional<ObservedRole> findByRealmCodeAndExternalRoleAndSource(String realmCode, String externalRole, RoleSource source);
}
