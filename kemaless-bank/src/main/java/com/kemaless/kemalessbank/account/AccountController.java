package com.kemaless.kemalessbank.account;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/accounts")
    public List<AccountResponse> accounts(Principal principal) {
        return accountService.findUserAccounts(principal.getName()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping("/api/accounts")
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request, Principal principal) {
        return AccountResponse.from(accountService.openAccount(principal.getName(), request));
    }

    @PostMapping("/api/accounts/{id}/close")
    public AccountResponse closeAccount(@PathVariable Long id, Principal principal) {
        return AccountResponse.from(accountService.closeAccount(principal.getName(), id));
    }

    @PostMapping("/api/accounts/{id}/block")
    public AccountResponse blockAccount(@PathVariable Long id, Principal principal) {
        return AccountResponse.from(accountService.blockAccount(principal.getName(), id));
    }

    @PostMapping("/api/accounts/{id}/unblock")
    public AccountResponse unblockAccount(@PathVariable Long id, Principal principal) {
        return AccountResponse.from(accountService.unblockAccount(principal.getName(), id));
    }
}
