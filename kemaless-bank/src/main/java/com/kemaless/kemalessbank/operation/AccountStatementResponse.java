package com.kemaless.kemalessbank.operation;

import com.kemaless.kemalessbank.account.AccountResponse;
import java.time.OffsetDateTime;
import java.util.List;

public record AccountStatementResponse(
        AccountResponse account,
        OffsetDateTime from,
        OffsetDateTime to,
        List<OperationResponse> operations) {
}
