package com.kemaless.kemalessbank.operation;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
    }

    @GetMapping("/api/operations")
    public List<OperationResponse> history(Principal principal) {
        return operationService.history(principal.getName());
    }

    @GetMapping("/api/accounts/{id}/statement")
    public AccountStatementResponse statement(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            Principal principal) {
        return operationService.statement(principal.getName(), id, from, to);
    }

    @PostMapping("/api/transfers/internal")
    public TransferResponse transfer(@Valid @RequestBody InternalTransferRequest request, Principal principal) {
        return operationService.transferBetweenOwnAccounts(principal.getName(), request);
    }

    @PostMapping("/api/transfers/peer")
    public TransferResponse peerTransfer(@Valid @RequestBody PeerTransferRequest request, Principal principal) {
        return operationService.transferToAnotherUser(principal.getName(), request);
    }
}
