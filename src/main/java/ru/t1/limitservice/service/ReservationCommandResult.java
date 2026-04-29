package ru.t1.limitservice.service;

import ru.t1.limitservice.api.ReservationResponse;
import ru.t1.limitservice.entity.OperationStatus;

import java.math.BigDecimal;

public record ReservationCommandResult(
        String operationId,
        String userId,
        BigDecimal amount,
        OperationStatus status,
        BigDecimal availableAmount,
        boolean created
) {

    public ReservationResponse toResponse() {
        return new ReservationResponse(operationId, userId, amount, status, availableAmount);
    }
}
