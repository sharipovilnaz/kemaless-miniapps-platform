package com.kemaless.kemalessbank.account;

import com.kemaless.kemalessbank.user.BankUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findAllByUserOrderByIdAsc(BankUser user);

    @Query("""
            select a from BankAccount a
            where a.user = :user
            order by
              case a.status
                when 'ACTIVE' then 0
                when 'BLOCKED' then 1
                else 2
              end,
              a.createdAt desc,
              a.id desc
            """)
    List<BankAccount> findAllVisibleByUser(@Param("user") BankUser user);

    @Query("select coalesce(sum(a.balance), 0) from BankAccount a where a.user = :user and a.status = 'ACTIVE'")
    BigDecimal sumActiveBalanceByUser(@Param("user") BankUser user);

    Optional<BankAccount> findByIdAndUser(Long id, BankUser user);

    boolean existsByUser(BankUser user);

    boolean existsByAccountNumber(String accountNumber);

    long countByUserAndStatus(BankUser user, AccountStatus status);

    Optional<BankAccount> findByAccountNumber(String accountNumber);
}
