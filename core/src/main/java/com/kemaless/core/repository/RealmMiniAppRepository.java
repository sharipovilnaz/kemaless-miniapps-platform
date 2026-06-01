package com.kemaless.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kemaless.core.enums.RealmMiniAppStatus;
import com.kemaless.core.model.RealmMiniApp;

@Repository
public interface RealmMiniAppRepository extends JpaRepository<RealmMiniApp, UUID> {
    boolean existsByRealmCodeAndMiniAppCodeAndStatusNot(String realmCode, String miniAppCode, RealmMiniAppStatus status);

    Optional<RealmMiniApp> findByRealmCodeAndMiniAppCodeAndStatusNot(String realmCode, String miniAppCode, RealmMiniAppStatus status);

    @Query("""
        SELECT rma FROM RealmMiniApp rma 
        WHERE rma.realm.code = :realmCode 
        AND rma.status != 'DELETED'
        AND (:status = '' OR CAST(rma.status AS string) = :status)
        AND (:search = '' OR LOWER(rma.miniApp.name) LIKE LOWER(CONCAT('%', :search, '%')) 
                          OR LOWER(rma.miniApp.code) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<RealmMiniApp> findAllWithFilters(
            @Param("realmCode") String realmCode,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
        SELECT rma FROM RealmMiniApp rma 
        JOIN FETCH rma.miniApp ma
        JOIN rma.realm r
        WHERE r.code = :realmCode 
        AND CAST(r.status AS string) = 'ACTIVE' 
        AND CAST(rma.status AS string) = 'PUBLISHED'
    """)
    List<RealmMiniApp> findAllPublishedInActiveRealm(@Param("realmCode") String realmCode);

    @Query("""
        SELECT rma FROM RealmMiniApp rma
        JOIN FETCH rma.miniApp ma
        JOIN rma.realm r
        WHERE r.code = :realmCode
          AND rma.status <> com.kemaless.core.enums.RealmMiniAppStatus.DELETED
    """)
    List<RealmMiniApp> findAllActiveByRealmCode(@Param("realmCode") String realmCode);

    boolean existsByMiniApp_Id(UUID miniAppId);
}
