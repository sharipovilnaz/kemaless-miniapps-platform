package com.kemaless.kemalessbank.account;

import com.kemaless.kemalessbank.user.BankUser;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
            select upper(a.currency), coalesce(sum(a.balance), 0)
            from BankAccount a
            where a.user = :user and a.status = 'ACTIVE'
            group by upper(a.currency)
            order by upper(a.currency) asc
            """)
    List<Object[]> sumActiveBalancesByCurrencyRows(@Param("user") BankUser user);

    default Map<String, BigDecimal> sumActiveBalancesByCurrency(BankUser user) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : sumActiveBalancesByCurrencyRows(user)) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                result.put(row[0].toString(), (BigDecimal) row[1]);
            }
        }
        return result;
    }

    Optional<BankAccount> findByIdAndUser(Long id, BankUser user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id = :id and a.user = :user")
    Optional<BankAccount> findByIdAndUserForUpdate(@Param("id") Long id, @Param("user") BankUser user);

    // Захват блокировок в фиксированном порядке id предотвращает взаимные блокировки при встречных переводах.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.id in :ids order by a.id asc")
    List<BankAccount> findAllByIdInOrderByIdAscForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccount a where a.user = :user and a.status = :status order by a.id asc")
    List<BankAccount> findAllByUserAndStatusOrderByIdAscForUpdate(
            @Param("user") BankUser user,
            @Param("status") AccountStatus status);

    @Query("select upper(a.currency) from BankAccount a where a.id = :id and a.user = :user")
    Optional<String> findCurrencyByIdAndUser(@Param("id") Long id, @Param("user") BankUser user);

    @Query("""
            select a.id
            from BankAccount a
            where a.user = :user and a.status = 'ACTIVE' and upper(a.currency) = :currency
            order by a.id asc
            """)
    List<Long> findActiveIdsByUserAndCurrencyOrderByIdAsc(
            @Param("user") BankUser user,
            @Param("currency") String currency);

    @Query("""
            select new com.kemaless.kemalessbank.account.AccountTargetProjection(
                a.id, a.user.id, a.status, upper(a.currency))
            from BankAccount a
            where a.accountNumber = :accountNumber
            """)
    Optional<AccountTargetProjection> findTargetProjectionByAccountNumber(@Param("accountNumber") String accountNumber);

    boolean existsByUser(BankUser user);

    boolean existsByAccountNumber(String accountNumber);

    long countByUserAndStatus(BankUser user, AccountStatus status);

    Optional<BankAccount> findByAccountNumber(String accountNumber);
}
