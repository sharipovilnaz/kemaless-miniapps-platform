package com.kemaless.kemalessbank.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UserDto(
        String username,
        String fullName,
        String email,
        String phone,
        BigDecimal balance,
        String currency,
        List<String> roles,
        Map<String, BigDecimal> balancesByCurrency) {

    public UserDto {
        roles = roles == null ? List.of() : List.copyOf(roles);
        balancesByCurrency = balancesByCurrency == null ? Map.of() : Map.copyOf(balancesByCurrency);
    }

    public static UserDto from(BankUser user, BigDecimal rubActiveBalance) {
        BigDecimal normalizedRub = rubActiveBalance == null ? BigDecimal.ZERO : rubActiveBalance;
        return new UserDto(
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                normalizedRub,
                "RUB",
                user.roles(),
                normalizedRub.compareTo(BigDecimal.ZERO) == 0 ? Map.of() : Map.of("RUB", normalizedRub));
    }

    public static UserDto from(BankUser user, Map<String, BigDecimal> balancesByCurrency) {
        Map<String, BigDecimal> normalized = balancesByCurrency == null ? Map.of() : balancesByCurrency;
        return new UserDto(
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                normalized.getOrDefault("RUB", BigDecimal.ZERO),
                "RUB",
                user.roles(),
                normalized);
    }
}
