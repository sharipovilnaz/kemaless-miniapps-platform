package com.kemaless.kemalessbank.user;

import java.math.BigDecimal;
import java.util.List;

public record UserDto(
        String username,
        String fullName,
        String email,
        String phone,
        BigDecimal balance,
        String currency,
        List<String> roles) {

    public static UserDto from(BankUser user, BigDecimal aggregateActiveAccountsBalance) {
        return new UserDto(
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                aggregateActiveAccountsBalance,
                "RUB",
                user.roles());
    }
}
