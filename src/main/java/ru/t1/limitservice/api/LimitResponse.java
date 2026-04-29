package ru.t1.limitservice.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LimitResponse(
        String userId,
        BigDecimal availableAmount,
        BigDecimal defaultAmount,
        OffsetDateTime updatedAt
) {
}
