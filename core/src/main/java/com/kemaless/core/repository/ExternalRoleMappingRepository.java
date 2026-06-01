package com.kemaless.core.repository;

import com.kemaless.core.model.ExternalRoleMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

@Repository
public interface ExternalRoleMappingRepository extends JpaRepository<ExternalRoleMapping, UUID> {
    boolean existsByRealmRole_Id(UUID realmRoleId);
    List<ExternalRoleMapping> findAllByRealmCode(String realmCode);
    Optional<ExternalRoleMapping> findByRealmCodeAndId(String realmCode, UUID id);
    boolean existsByRealmCodeAndExternalRole(String realmCode, String externalRole);
    List<ExternalRoleMapping> findByRealmCodeAndExternalRoleIn(String realmCode, Collection<String> externalRoles);
}
