package com.kemaless.kemalessbank.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankUserDeviceRepository extends JpaRepository<BankUserDevice, Long> {

    @Query("SELECT d FROM BankUserDevice d WHERE d.user.id = :userId AND d.fingerprint = :fp")
    Optional<BankUserDevice> findForUser(@Param("userId") long userId, @Param("fp") String fp);

    @Query("SELECT d FROM BankUserDevice d WHERE d.user.id = :userId ORDER BY d.lastSeenAt DESC")
    List<BankUserDevice> findAllForUser(@Param("userId") long userId);

    // Атомарный upsert предотвращает гонку по (user_id, fingerprint) при одновременных запросах входа/обновления.
    @Modifying
    @Query(
            value = """
                    INSERT INTO bank_user_device (user_id, fingerprint, user_agent, ip_address, first_seen_at, last_seen_at)
                    VALUES (:userId, :fp, :ua, :ip, :now, :now)
                    ON CONFLICT (user_id, fingerprint)
                    DO UPDATE SET
                      last_seen_at = EXCLUDED.last_seen_at,
                      ip_address = CASE WHEN EXCLUDED.ip_address <> '' THEN EXCLUDED.ip_address ELSE bank_user_device.ip_address END,
                      user_agent = CASE WHEN TRIM(EXCLUDED.user_agent) <> '' THEN EXCLUDED.user_agent ELSE bank_user_device.user_agent END
                    """,
            nativeQuery = true)
    void upsertDevice(
            @Param("userId") long userId,
            @Param("fp") String fp,
            @Param("ua") String ua,
            @Param("ip") String ip,
            @Param("now") OffsetDateTime now);
}
