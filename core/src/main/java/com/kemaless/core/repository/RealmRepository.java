package com.kemaless.core.repository;

import com.kemaless.core.dto.response.RealmListItemResponse;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.model.Realm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RealmRepository extends JpaRepository<Realm, UUID> {
    Optional<Realm> findByCode(String code);
    boolean existsByCode(String code);

    @Query("""
        SELECT new com.kemaless.core.dto.response.RealmListItemResponse(
            r.code, 
            r.name, 
            r.status,
            (SELECT COUNT(ma) FROM RealmMiniApp ma WHERE ma.realm.id = r.id),
            (SELECT COUNT(c) FROM RealmClient c WHERE c.realm.id = r.id AND c.status = com.kemaless.core.enums.ClientStatus.ACTIVE),
            r.createdAt
        )
        FROM Realm r
        WHERE r.status != com.kemaless.core.enums.RealmStatus.DELETED
        AND (:status IS NULL OR r.status = :status)
        AND (:searchPattern IS NULL OR r.name LIKE :searchPattern OR r.code LIKE :searchPattern)
    """)
    Page<RealmListItemResponse> findAllWithCounts(
            @Param("searchPattern") String searchPattern,
            @Param("status") RealmStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(ma) FROM RealmMiniApp ma WHERE ma.realm.code = :realmCode")
    long countConnectedMiniApps(@Param("realmCode") String realmCode);

    @Query("SELECT COUNT(c) FROM RealmClient c WHERE c.realm.code = :realmCode AND c.status = com.kemaless.core.enums.ClientStatus.ACTIVE")
    long countActiveClients(@Param("realmCode") String realmCode);

    @Query("SELECT COUNT(rr) FROM RealmRole rr WHERE rr.realm.code = :realmCode")
    long countRealmRoles(@Param("realmCode") String realmCode);
}
