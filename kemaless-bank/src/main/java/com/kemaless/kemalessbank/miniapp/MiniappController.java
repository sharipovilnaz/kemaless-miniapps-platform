package com.kemaless.kemalessbank.miniapp;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MiniappController {

    private final MiniappService miniappService;

    public MiniappController(MiniappService miniappService) {
        this.miniappService = miniappService;
    }

    @GetMapping({"/api/miniapps/catalog", "/api/miniapps"})
    public List<MiniappCatalogItem> catalog() {
        return miniappService.catalog();
    }

    @PostMapping("/api/miniapps/{miniAppCode}/launch")
    public MiniappLaunchSessionResponse launch(
            @PathVariable String miniAppCode,
            Principal principal,
            @Valid @RequestBody(required = false) MiniappLaunchOverrides overrides) {
        return miniappService.launch(principal.getName(), miniAppCode, overrides);
    }
}
