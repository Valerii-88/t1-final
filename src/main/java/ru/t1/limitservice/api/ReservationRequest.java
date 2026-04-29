package ru.t1.limitservice.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReservationRequest(
        @NotBlank(message = "operationId must not be blank")
        @Size(max = 128, message = "operationId must not exceed 128 characters")
        String operationId,
        @NotBlank(message = "userId must not be blank")
        @Size(max = 128, message = "userId must not exceed 128 characters")
        String userId,
        @NotNull(message = "amount must not be null")
        @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "amount must have up to 2 decimal places")
        BigDecimal amount
) {
}
