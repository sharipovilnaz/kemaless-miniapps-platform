package com.kemaless.core.auth.repository;

import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByUsername(String username);

    boolean existsByGlobalRole(GlobalRole role);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("""
            SELECT u FROM PlatformUser u
            WHERE (:search IS NULL OR :search = ''
                OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                OR (u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))))
            AND (:status IS NULL OR u.status = :status)
            AND (:role IS NULL OR u.globalRole = :role)
            AND (:realmCode IS NULL OR :realmCode = ''
                OR (u.realm IS NOT NULL AND u.realm.code = :realmCode))
            """)
    Page<PlatformUser> findAllWithFilters(
            @Param("search") String search,
            @Param("status") PlatformUserStatus status,
            @Param("role") GlobalRole role,
            @Param("realmCode") String realmCode,
            Pageable pageable
    );
}
