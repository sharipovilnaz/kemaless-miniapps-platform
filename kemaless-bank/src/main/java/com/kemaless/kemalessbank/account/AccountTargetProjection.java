package com.kemaless.kemalessbank.account;

public record AccountTargetProjection(Long id, Long userId, AccountStatus status, String currency) {
}
