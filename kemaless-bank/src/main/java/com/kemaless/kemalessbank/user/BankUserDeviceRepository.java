package com.kemaless.kemalessbank.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankUserDeviceRepository extends JpaRepository<BankUserDevice, Long> {

    @Query("SELECT d FROM BankUserDevice d WHERE d.user.id = :userId AND d.fingerprint = :fp")
    Optional<BankUserDevice> findForUser(@Param("userId") long userId, @Param("fp") String fp);

    @Query("SELECT d FROM BankUserDevice d WHERE d.user.id = :userId ORDER BY d.lastSeenAt DESC")
    List<BankUserDevice> findAllForUser(@Param("userId") long userId);
}
