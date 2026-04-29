package ru.t1.limitservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.t1.limitservice.entity.LimitOperation;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.error.OperationConflictException;
import ru.t1.limitservice.error.OperationNotFoundException;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceLifecycleTest {

    @Mock
    private LimitOperationRepository limitOperationRepository;

    @Mock
    private UserLimitRepository userLimitRepository;

    @Mock
    private LimitConfigRepository limitConfigRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void confirmMarksReservedOperationAsConfirmedWithoutChangingBalance() {
        LimitOperation operation = operation("op-1", "user-1", "250.00", OperationStatus.RESERVED);
        UserLimit userLimit = userLimit("user-1", "750.00");
        when(limitOperationRepository.findByOperationIdForUpdate("op-1")).thenReturn(Optional.of(operation));
        when(limitOperationRepository.saveAndFlush(any(LimitOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userLimitRepository.findById("user-1")).thenReturn(Optional.of(userLimit));

        ReservationCommandResult result = reservationService.confirm("op-1");

        ArgumentCaptor<LimitOperation> operationCaptor = ArgumentCaptor.forClass(LimitOperation.class);
        verify(limitOperationRepository).saveAndFlush(operationCaptor.capture());
        LimitOperation savedOperation = operationCaptor.getValue();
        assertThat(savedOperation.getStatus()).isEqualTo(OperationStatus.CONFIRMED);
        assertThat(savedOperation.getConfirmedAt()).isNotNull();
        assertThat(savedOperation.getCanceledAt()).isNull();

        assertThat(result.status()).isEqualTo(OperationStatus.CONFIRMED);
        assertThat(result.availableAmount()).isEqualByComparingTo("750.00");
        verify(userLimitRepository, never()).saveAndFlush(any(UserLimit.class));
    }

    @Test
    void confirmReturnsIdempotentSuccessForConfirmedOperation() {
        LimitOperation operation = operation("op-2", "user-1", "125.00", OperationStatus.CONFIRMED);
        operation.setConfirmedAt(OffsetDateTime.parse("2026-04-29T01:00:00Z"));
        UserLimit userLimit = userLimit("user-1", "875.00");

        when(limitOperationRepository.findByOperationIdForUpdate("op-2")).thenReturn(Optional.of(operation));
        when(userLimitRepository.findById("user-1")).thenReturn(Optional.of(userLimit));

        ReservationCommandResult result = reservationService.confirm("op-2");

        assertThat(result.status()).isEqualTo(OperationStatus.CONFIRMED);
        assertThat(result.availableAmount()).isEqualByComparingTo("875.00");
        verify(limitOperationRepository, never()).saveAndFlush(any(LimitOperation.class));
    }

    @Test
    void cancelRestoresAvailableAmountForReservedOperation() {
        LimitOperation operation = operation("op-3", "user-1", "125.00", OperationStatus.RESERVED);
        UserLimit userLimit = userLimit("user-1", "875.00");

        when(limitOperationRepository.findByOperationIdForUpdate("op-3")).thenReturn(Optional.of(operation));
        when(userLimitRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(userLimit));
        when(userLimitRepository.saveAndFlush(any(UserLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitOperationRepository.saveAndFlush(any(LimitOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationCommandResult result = reservationService.cancel("op-3");

        assertThat(userLimit.getAvailableAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.status()).isEqualTo(OperationStatus.CANCELED);
        assertThat(result.availableAmount()).isEqualByComparingTo("1000.00");

        ArgumentCaptor<LimitOperation> operationCaptor = ArgumentCaptor.forClass(LimitOperation.class);
        verify(limitOperationRepository).saveAndFlush(operationCaptor.capture());
        assertThat(operationCaptor.getValue().getStatus()).isEqualTo(OperationStatus.CANCELED);
        assertThat(operationCaptor.getValue().getCanceledAt()).isNotNull();
    }

    @Test
    void cancelReturnsIdempotentSuccessForCanceledOperation() {
        LimitOperation operation = operation("op-4", "user-1", "125.00", OperationStatus.CANCELED);
        operation.setCanceledAt(OffsetDateTime.parse("2026-04-29T01:00:00Z"));
        UserLimit userLimit = userLimit("user-1", "1000.00");

        when(limitOperationRepository.findByOperationIdForUpdate("op-4")).thenReturn(Optional.of(operation));
        when(userLimitRepository.findById("user-1")).thenReturn(Optional.of(userLimit));

        ReservationCommandResult result = reservationService.cancel("op-4");

        assertThat(result.status()).isEqualTo(OperationStatus.CANCELED);
        assertThat(result.availableAmount()).isEqualByComparingTo("1000.00");
        verify(limitOperationRepository, never()).saveAndFlush(any(LimitOperation.class));
        verify(userLimitRepository, never()).saveAndFlush(any(UserLimit.class));
    }

    @Test
    void confirmRejectsCanceledOperation() {
        LimitOperation operation = operation("op-5", "user-1", "125.00", OperationStatus.CANCELED);
        when(limitOperationRepository.findByOperationIdForUpdate("op-5")).thenReturn(Optional.of(operation));

        assertThatThrownBy(() -> reservationService.confirm("op-5"))
                .isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("op-5");
    }

    @Test
    void cancelRejectsConfirmedOperation() {
        LimitOperation operation = operation("op-6", "user-1", "125.00", OperationStatus.CONFIRMED);
        when(limitOperationRepository.findByOperationIdForUpdate("op-6")).thenReturn(Optional.of(operation));

        assertThatThrownBy(() -> reservationService.cancel("op-6"))
                .isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("op-6");
    }

    @Test
    void confirmRejectsMissingOperation() {
        when(limitOperationRepository.findByOperationIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.confirm("missing"))
                .isInstanceOf(OperationNotFoundException.class)
                .hasMessageContaining("missing");
    }

    private static LimitOperation operation(String operationId, String userId, String amount, OperationStatus status) {
        LimitOperation operation = new LimitOperation();
        operation.setOperationId(operationId);
        operation.setUserId(userId);
        operation.setAmount(new BigDecimal(amount));
        operation.setStatus(status);
        operation.setCreatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        return operation;
    }

    private static UserLimit userLimit(String userId, String availableAmount) {
        UserLimit userLimit = new UserLimit();
        userLimit.setUserId(userId);
        userLimit.setAvailableAmount(new BigDecimal(availableAmount));
        userLimit.setCreatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        userLimit.setUpdatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        return userLimit;
    }
}
