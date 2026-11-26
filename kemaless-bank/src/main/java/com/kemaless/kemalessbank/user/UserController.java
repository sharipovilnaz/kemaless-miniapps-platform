package com.kemaless.kemalessbank.user;

import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.common.MessageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;
    private final UserDeviceService userDeviceService;
    private final BankAccountRepository accountRepository;

    public UserController(
            UserService userService, UserDeviceService userDeviceService, BankAccountRepository accountRepository) {
        this.userService = userService;
        this.userDeviceService = userDeviceService;
        this.accountRepository = accountRepository;
    }

    @GetMapping({"/api/users/me", "/api/me"})
    public UserDto me(Principal principal) {
        BankUser user = userService.requireByUsername(principal.getName());
        return UserDto.from(user, accountRepository.sumActiveBalancesByCurrency(user));
    }

    @GetMapping("/api/users/me/devices")
    public List<UserDeviceResponse> myDevices(Principal principal, HttpServletRequest request) {
        return userDeviceService.listDevices(principal.getName(), request);
    }

    @PutMapping("/api/users/me")
    public UserDto updateMe(@Valid @RequestBody UpdateProfileRequest request, Principal principal) {
        BankUser user = userService.updateProfile(principal.getName(), request);
        return UserDto.from(user, accountRepository.sumActiveBalancesByCurrency(user));
    }

    @PutMapping("/api/users/me/password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        userService.changePassword(principal.getName(), request);
        return new MessageResponse("Password changed");
    }
}
