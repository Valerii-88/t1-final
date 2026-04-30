package ru.t1.limitservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.t1.limitservice.api.ReservationRequest;
import ru.t1.limitservice.entity.LimitConfig;
import ru.t1.limitservice.entity.LimitOperation;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.error.InsufficientLimitException;
import ru.t1.limitservice.error.OperationConflictException;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ReservationServiceReserveTest {

    @Mock
    private LimitOperationRepository limitOperationRepository;

    @Mock
    private UserLimitRepository userLimitRepository;

    @Mock
    private LimitConfigRepository limitConfigRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void reserveCreatesNewOperationAndDecreasesAvailableAmount() {
        ReservationRequest request = new ReservationRequest("op-1", "user-1", new BigDecimal("250.00"));
        LimitConfig config = limitConfig(new BigDecimal("1000.00"));
        when(limitOperationRepository.findByOperationIdForUpdate("op-1")).thenReturn(Optional.empty());
        when(userLimitRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.empty());
        when(limitConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(userLimitRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);
        when(userLimitRepository.saveAndFlush(any(UserLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitOperationRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1);

        ReservationCommandResult result = reservationService.reserve(request);

        ArgumentCaptor<UserLimit> userCaptor = ArgumentCaptor.forClass(UserLimit.class);
        verify(userLimitRepository, times(1)).saveAndFlush(userCaptor.capture());
        UserLimit persistedUser = userCaptor.getValue();
        assertThat(persistedUser.getAvailableAmount()).isEqualByComparingTo("750.00");
        verify(userLimitRepository).insertIfAbsent(any(), any(), any(), any());
        verify(limitOperationRepository).insertIfAbsent(any(), any(), any(), any(), any());

        assertThat(result.operationId()).isEqualTo("op-1");
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.amount()).isEqualByComparingTo("250.00");
        assertThat(result.status()).isEqualTo(OperationStatus.RESERVED);
        assertThat(result.availableAmount()).isEqualByComparingTo("750.00");
        assertThat(result.created()).isTrue();
    }

    @Test
    void reserveReturnsExistingOperationIdempotentlyForSamePayload() {
        ReservationRequest request = new ReservationRequest("op-1", "user-1", new BigDecimal("250.00"));
        LimitOperation existingOperation = operation("op-1", "user-1", "250.00", OperationStatus.RESERVED);
        UserLimit userLimit = userLimit("user-1", "750.00");

        when(limitOperationRepository.findByOperationIdForUpdate("op-1")).thenReturn(Optional.of(existingOperation));
        when(userLimitRepository.findById("user-1")).thenReturn(Optional.of(userLimit));

        ReservationCommandResult result = reservationService.reserve(request);

        assertThat(result.operationId()).isEqualTo("op-1");
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.amount()).isEqualByComparingTo("250.00");
        assertThat(result.status()).isEqualTo(OperationStatus.RESERVED);
        assertThat(result.availableAmount()).isEqualByComparingTo("750.00");
        assertThat(result.created()).isFalse();
        verify(userLimitRepository, never()).saveAndFlush(any(UserLimit.class));
        verify(limitOperationRepository, never()).saveAndFlush(any(LimitOperation.class));
    }

    @Test
    void reserveRejectsExistingOperationWithDifferentPayload() {
        ReservationRequest request = new ReservationRequest("op-1", "user-2", new BigDecimal("250.00"));
        LimitOperation existingOperation = operation("op-1", "user-1", "200.00", OperationStatus.RESERVED);

        when(limitOperationRepository.findByOperationIdForUpdate("op-1")).thenReturn(Optional.of(existingOperation));

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("op-1");

        verify(userLimitRepository, never()).saveAndFlush(any(UserLimit.class));
        verify(limitOperationRepository, never()).saveAndFlush(any(LimitOperation.class));
    }

    @Test
    void reserveRejectsWhenAvailableAmountIsInsufficient() {
        ReservationRequest request = new ReservationRequest("op-9", "user-1", new BigDecimal("150.00"));
        UserLimit userLimit = userLimit("user-1", "100.00");

        when(limitOperationRepository.findByOperationIdForUpdate("op-9")).thenReturn(Optional.empty());
        when(userLimitRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(userLimit));

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(InsufficientLimitException.class)
                .hasMessageContaining("user-1");

        assertThat(userLimit.getAvailableAmount()).isEqualByComparingTo("100.00");
        verify(limitOperationRepository, never()).saveAndFlush(any(LimitOperation.class));
    }

    @Test
    void reserveUsesExistingUserWhenInsertIfAbsentLosesCreationRace() {
        ReservationRequest request = new ReservationRequest("op-12", "user-12", new BigDecimal("250.00"));
        LimitConfig config = limitConfig(new BigDecimal("1000.00"));
        UserLimit existingUser = userLimit("user-12", "1000.00");

        when(limitOperationRepository.findByOperationIdForUpdate("op-12")).thenReturn(Optional.empty());
        when(userLimitRepository.findByUserIdForUpdate("user-12")).thenReturn(Optional.empty(), Optional.of(existingUser));
        when(limitConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(userLimitRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);
        when(userLimitRepository.saveAndFlush(any(UserLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitOperationRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(1);

        ReservationCommandResult result = reservationService.reserve(request);

        assertThat(result.operationId()).isEqualTo("op-12");
        assertThat(result.userId()).isEqualTo("user-12");
        assertThat(result.amount()).isEqualByComparingTo("250.00");
        assertThat(result.status()).isEqualTo(OperationStatus.RESERVED);
        assertThat(result.availableAmount()).isEqualByComparingTo("750.00");
        assertThat(result.created()).isTrue();
        verify(userLimitRepository, times(1)).saveAndFlush(any(UserLimit.class));
    }

    @Test
    void reserveReturnsIdempotentResultWhenOperationInsertCollidesWithSamePayload() {
        ReservationRequest request = new ReservationRequest("op-10", "user-1", new BigDecimal("250.00"));
        UserLimit userLimit = userLimit("user-1", "750.00");
        LimitOperation persistedOperation = operation("op-10", "user-1", "250.00", OperationStatus.RESERVED);
        List<BigDecimal> savedAmounts = new ArrayList<>();

        when(limitOperationRepository.findByOperationIdForUpdate("op-10"))
                .thenReturn(Optional.empty(), Optional.of(persistedOperation));
        when(userLimitRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(userLimit));
        when(userLimitRepository.findById("user-1")).thenReturn(Optional.of(userLimit));
        when(userLimitRepository.saveAndFlush(any(UserLimit.class))).thenAnswer(invocation -> {
            UserLimit savedUser = invocation.getArgument(0);
            savedAmounts.add(savedUser.getAvailableAmount());
            return savedUser;
        });
        when(limitOperationRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);

        ReservationCommandResult result = reservationService.reserve(request);

        assertThat(result.operationId()).isEqualTo("op-10");
        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.amount()).isEqualByComparingTo("250.00");
        assertThat(result.status()).isEqualTo(OperationStatus.RESERVED);
        assertThat(result.availableAmount()).isEqualByComparingTo("750.00");
        assertThat(result.created()).isFalse();

        verify(userLimitRepository, times(2)).saveAndFlush(any(UserLimit.class));
        assertThat(savedAmounts).containsExactly(new BigDecimal("500.00"), new BigDecimal("750.00"));
        assertThat(userLimit.getAvailableAmount()).isEqualByComparingTo("750.00");
    }

    @Test
    void reserveRejectsWithConflictWhenOperationInsertCollidesWithDifferentPersistedPayload() {
        ReservationRequest request = new ReservationRequest("op-11", "user-1", new BigDecimal("250.00"));
        UserLimit userLimit = userLimit("user-1", "750.00");
        LimitOperation persistedOperation = operation("op-11", "user-9", "300.00", OperationStatus.RESERVED);
        List<BigDecimal> savedAmounts = new ArrayList<>();

        when(limitOperationRepository.findByOperationIdForUpdate("op-11"))
                .thenReturn(Optional.empty(), Optional.of(persistedOperation));
        when(userLimitRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(userLimit));
        when(userLimitRepository.saveAndFlush(any(UserLimit.class))).thenAnswer(invocation -> {
            UserLimit savedUser = invocation.getArgument(0);
            savedAmounts.add(savedUser.getAvailableAmount());
            return savedUser;
        });
        when(limitOperationRepository.insertIfAbsent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(OperationConflictException.class)
                .hasMessageContaining("op-11");

        verify(userLimitRepository, times(2)).saveAndFlush(any(UserLimit.class));
        assertThat(savedAmounts).containsExactly(new BigDecimal("500.00"), new BigDecimal("750.00"));
        assertThat(userLimit.getAvailableAmount()).isEqualByComparingTo("750.00");
    }

    private static LimitConfig limitConfig(BigDecimal defaultAmount) {
        LimitConfig config = new LimitConfig();
        config.setId(1L);
        config.setDefaultLimit(defaultAmount);
        config.setCreatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        config.setUpdatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        return config;
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
