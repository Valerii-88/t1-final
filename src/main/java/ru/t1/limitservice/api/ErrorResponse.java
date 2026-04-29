package ru.t1.limitservice.api;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        String path
) {
}
