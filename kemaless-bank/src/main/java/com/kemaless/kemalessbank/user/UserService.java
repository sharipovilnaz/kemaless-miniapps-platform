package com.kemaless.kemalessbank.user;

import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.common.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserService {

    private final BankUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(BankUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public BankUser requireByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));
    }

    @Transactional
    public BankUser updateProfile(String username, UpdateProfileRequest request) {
        if (userRepository.existsByEmailAndUsernameNot(request.email(), username)) {
            throw new ConflictException("Этот email уже используется другим аккаунтом");
        }
        BankUser user = requireByUsername(username);
        user.updateProfile(request.fullName(), request.email(), blankToNull(request.phone()));
        log.info("User profile updated: username={}", username);
        return user;
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        BankUser user = requireByUsername(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Password change rejected: username={}, reason=current-password-mismatch", username);
            throw new BadRequestException("Текущий пароль указан неверно");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            log.warn("Password change rejected: username={}, reason=same-password", username);
            throw new BadRequestException("Новый пароль должен отличаться от текущего");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("Password changed: username={}", username);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
