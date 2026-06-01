package com.kemaless.kemalessbank.operation;

import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.user.BankUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRepository extends JpaRepository<BankOperation, UUID> {

    List<BankOperation> findAllByUserOrderByCreatedAtDesc(BankUser user);

    List<BankOperation> findAllByAccountAndCreatedAtBetweenOrderByCreatedAtDesc(
            BankAccount account,
            OffsetDateTime from,
            OffsetDateTime to);
}
