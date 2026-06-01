package com.kemaless.kemalessbank.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankUserRepository extends JpaRepository<BankUser, Long> {

    Optional<BankUser> findByUsername(String username);

    Optional<BankUser> findByUsernameIgnoreCase(String username);

    @Query(
            value =
                    "SELECT * FROM bank_user WHERE regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g') = :digits",
            nativeQuery = true)
    List<BankUser> findAllByPhoneDigits(@Param("digits") String digits);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndUsernameNot(String email, String username);
}
