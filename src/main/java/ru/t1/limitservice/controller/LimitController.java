package ru.t1.limitservice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.t1.limitservice.api.LimitResponse;
import ru.t1.limitservice.api.ReservationRequest;
import ru.t1.limitservice.api.ReservationResponse;
import ru.t1.limitservice.service.LimitQueryService;
import ru.t1.limitservice.service.ReservationCommandResult;
import ru.t1.limitservice.service.ReservationService;

@Validated
@RestController
@RequestMapping("/api/v1/limits")
public class LimitController {

    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private final LimitQueryService limitQueryService;
    private final ReservationService reservationService;

    public LimitController(LimitQueryService limitQueryService, ReservationService reservationService) {
        this.limitQueryService = limitQueryService;
        this.reservationService = reservationService;
    }

    @GetMapping("/{userId}")
    public LimitResponse getLimit(
            @PathVariable
            @Size(max = 128, message = "userId must not exceed 128 characters")
            String userId
    ) {
        validateIdentifierLength(userId, "userId");
        return limitQueryService.getLimit(userId);
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationCommandResult result = reservationService.reserve(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.toResponse());
    }

    @PostMapping("/reservations/{operationId}/confirm")
    public ReservationResponse confirm(
            @PathVariable
            @Size(max = 128, message = "operationId must not exceed 128 characters")
            String operationId
    ) {
        validateIdentifierLength(operationId, "operationId");
        return reservationService.confirm(operationId).toResponse();
    }

    @PostMapping("/reservations/{operationId}/cancel")
    public ReservationResponse cancel(
            @PathVariable
            @Size(max = 128, message = "operationId must not exceed 128 characters")
            String operationId
    ) {
        validateIdentifierLength(operationId, "operationId");
        return reservationService.cancel(operationId).toResponse();
    }

    private void validateIdentifierLength(String value, String fieldName) {
        if (value != null && value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must not exceed 128 characters");
        }
    }
}
