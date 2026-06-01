package com.kemaless.kemalessbank.operation;

import com.kemaless.kemalessbank.account.AccountResponse;
import java.util.List;

public record TransferResponse(
        AccountResponse fromAccount,
        AccountResponse toAccount,
        List<OperationResponse> operations) {
}
