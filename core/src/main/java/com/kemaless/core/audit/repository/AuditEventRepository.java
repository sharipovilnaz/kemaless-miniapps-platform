package com.kemaless.core.audit.repository;

import com.kemaless.core.audit.domain.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    @Query("""
            SELECT event
            FROM AuditEventEntity event
            WHERE (:realmCode IS NULL OR event.realmCode = :realmCode)
              AND (:fromTime IS NULL OR event.timestamp >= :fromTime)
              AND (:action IS NULL OR event.action = :action)
              AND (:status IS NULL OR event.status = :status)
            ORDER BY event.timestamp DESC
            """)
    List<AuditEventEntity> findRecentEvents(
            @Param("realmCode") String realmCode,
            @Param("fromTime") Instant fromTime,
            @Param("action") String action,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            SELECT
                event.realmCode AS realmCode,
                event.resourceId AS miniAppCode,
                event.action AS action,
                event.status AS status,
                COUNT(event) AS eventsCount
            FROM AuditEventEntity event
            WHERE event.resourceType = 'REALM_MINIAPP'
              AND event.realmCode = :realmCode
              AND event.timestamp >= :fromTime
              AND event.action IN ('LAUNCH_SUCCESS', 'LAUNCH_DENIED', 'SSO_EXCHANGE_SUCCESS', 'SSO_EXCHANGE_FAILED')
            GROUP BY event.realmCode, event.resourceId, event.action, event.status
            """)
    List<AuditMiniAppMetricsProjection> aggregateRealmMiniAppMetrics(
            @Param("realmCode") String realmCode,
            @Param("fromTime") Instant fromTime
    );
}
