package com.kemaless.kemalessbank.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRoleRepository extends JpaRepository<BankRole, Long> {

    Optional<BankRole> findByCodeIgnoreCase(String code);
}
