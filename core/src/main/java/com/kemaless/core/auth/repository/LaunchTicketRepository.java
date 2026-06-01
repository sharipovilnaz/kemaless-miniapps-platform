package com.kemaless.core.auth.repository;

import com.kemaless.core.auth.entity.LaunchTicket;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface LaunchTicketRepository extends JpaRepository<LaunchTicket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LaunchTicket> findByTicketHash(String ticketHash);
}
