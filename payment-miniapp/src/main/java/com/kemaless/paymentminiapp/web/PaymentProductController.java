package com.kemaless.paymentminiapp.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.kemaless.paymentminiapp.payment.PaymentProductService;
import com.kemaless.paymentminiapp.payment.PeerTransferCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentProductController {

    private final PaymentProductService paymentProductService;

    public PaymentProductController(PaymentProductService paymentProductService) {
        this.paymentProductService = paymentProductService;
    }

    @GetMapping("/me")
    public JsonNode me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return paymentProductService.currentUser(authorization);
    }

    @GetMapping("/accounts")
    public JsonNode accounts(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return paymentProductService.accounts(authorization);
    }

    @GetMapping("/operations")
    public JsonNode operations(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return paymentProductService.operations(authorization);
    }

    @PostMapping("/transfers/peer")
    public JsonNode peerTransfer(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody PeerTransferCommand body) {
        return paymentProductService.peerTransfer(authorization, body);
    }
}
