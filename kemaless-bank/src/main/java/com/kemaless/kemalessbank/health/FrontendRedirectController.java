package com.kemaless.kemalessbank.health;

import com.kemaless.kemalessbank.config.BankSecurityProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendRedirectController {

    private final BankSecurityProperties bankSecurityProperties;

    public FrontendRedirectController(BankSecurityProperties bankSecurityProperties) {
        this.bankSecurityProperties = bankSecurityProperties;
    }

    @GetMapping("/")
    public String frontend() {
        return "redirect:" + bankSecurityProperties.publicUiUrl();
    }
}
