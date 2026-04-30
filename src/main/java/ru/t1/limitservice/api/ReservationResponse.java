package ru.t1.limitservice.api;

import ru.t1.limitservice.entity.OperationStatus;

import java.math.BigDecimal;

public record ReservationResponse(
        String operationId,
        String userId,
        BigDecimal amount,
        OperationStatus status,
        BigDecimal availableAmount
) {
}
