package ru.t1.limitservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.t1.limitservice.api.ReservationRequest;
import ru.t1.limitservice.entity.LimitConfig;
import ru.t1.limitservice.entity.LimitOperation;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.error.InsufficientLimitException;
import ru.t1.limitservice.error.OperationConflictException;
import ru.t1.limitservice.error.OperationNotFoundException;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class ReservationService {

    private static final long DEFAULT_CONFIG_ID = 1L;

    private final LimitOperationRepository limitOperationRepository;
    private final UserLimitRepository userLimitRepository;
    private final LimitConfigRepository limitConfigRepository;

    public ReservationService(
            LimitOperationRepository limitOperationRepository,
            UserLimitRepository userLimitRepository,
            LimitConfigRepository limitConfigRepository
    ) {
        this.limitOperationRepository = limitOperationRepository;
        this.userLimitRepository = userLimitRepository;
        this.limitConfigRepository = limitConfigRepository;
    }

    @Transactional
    public ReservationCommandResult reserve(ReservationRequest request) {
        LimitOperation existingOperation = limitOperationRepository.findByOperationIdForUpdate(request.operationId()).orElse(null);
        if (existingOperation != null) {
            return existingReservation(request, existingOperation);
        }

        UserLimit userLimit = getOrCreateUserForUpdate(request.userId());
        BigDecimal originalAvailableAmount = userLimit.getAvailableAmount();
        OffsetDateTime originalUpdatedAt = userLimit.getUpdatedAt();
        BigDecimal remaining = userLimit.getAvailableAmount().subtract(request.amount());
        if (remaining.signum() < 0) {
            throw new InsufficientLimitException("Not enough limit for " + request.userId());
        }

        OffsetDateTime now = OffsetDateTime.now();
        userLimit.setAvailableAmount(remaining);
        userLimit.setUpdatedAt(now);
        userLimitRepository.saveAndFlush(userLimit);

        LimitOperation operation = new LimitOperation();
        operation.setOperationId(request.operationId());
        operation.setUserId(request.userId());
        operation.setAmount(request.amount());
        operation.setStatus(OperationStatus.RESERVED);
        operation.setCreatedAt(now);
        int inserted = limitOperationRepository.insertIfAbsent(
                operation.getOperationId(),
                operation.getUserId(),
                operation.getAmount(),
                operation.getStatus().name(),
                operation.getCreatedAt()
        );
        if (inserted == 0) {
            userLimit.setAvailableAmount(originalAvailableAmount);
            userLimit.setUpdatedAt(originalUpdatedAt);
            userLimitRepository.saveAndFlush(userLimit);

            return existingReservation(request, getOperationForUpdate(request.operationId()));
        }

        return new ReservationCommandResult(
                operation.getOperationId(),
                operation.getUserId(),
                operation.getAmount(),
                operation.getStatus(),
                userLimit.getAvailableAmount(),
                true
        );
    }

    @Transactional
    public ReservationCommandResult confirm(String operationId) {
        LimitOperation operation = getOperationForUpdate(operationId);
        if (operation.getStatus() == OperationStatus.CANCELED) {
            throw new OperationConflictException("Operation already canceled: " + operationId);
        }

        if (operation.getStatus() == OperationStatus.RESERVED) {
            operation.setStatus(OperationStatus.CONFIRMED);
            operation.setConfirmedAt(OffsetDateTime.now());
            limitOperationRepository.saveAndFlush(operation);
        }

        return toCommandResult(operation, false);
    }

    @Transactional
    public ReservationCommandResult cancel(String operationId) {
        LimitOperation operation = getOperationForUpdate(operationId);
        if (operation.getStatus() == OperationStatus.CONFIRMED) {
            throw new OperationConflictException("Operation already confirmed: " + operationId);
        }

        if (operation.getStatus() == OperationStatus.RESERVED) {
            UserLimit userLimit = userLimitRepository.findByUserIdForUpdate(operation.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found for operation " + operationId));

            userLimit.setAvailableAmount(userLimit.getAvailableAmount().add(operation.getAmount()));
            userLimit.setUpdatedAt(OffsetDateTime.now());
            userLimitRepository.saveAndFlush(userLimit);

            operation.setStatus(OperationStatus.CANCELED);
            operation.setCanceledAt(OffsetDateTime.now());
            limitOperationRepository.saveAndFlush(operation);

            return new ReservationCommandResult(
                    operation.getOperationId(),
                    operation.getUserId(),
                    operation.getAmount(),
                    operation.getStatus(),
                    userLimit.getAvailableAmount(),
                    false
            );
        }

        return toCommandResult(operation, false);
    }

    private ReservationCommandResult existingReservation(ReservationRequest request, LimitOperation existingOperation) {
        if (!existingOperation.getUserId().equals(request.userId())
                || existingOperation.getAmount().compareTo(request.amount()) != 0) {
            throw new OperationConflictException("Operation payload conflict for " + request.operationId());
        }

        UserLimit userLimit = userLimitRepository.findById(existingOperation.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for operation " + existingOperation.getOperationId()));

        return new ReservationCommandResult(
                existingOperation.getOperationId(),
                existingOperation.getUserId(),
                existingOperation.getAmount(),
                existingOperation.getStatus(),
                userLimit.getAvailableAmount(),
                false
        );
    }

    private UserLimit getOrCreateUserForUpdate(String userId) {
        UserLimit existing = userLimitRepository.findByUserIdForUpdate(userId).orElse(null);
        if (existing != null) {
            return existing;
        }

        LimitConfig config = limitConfigRepository.findById(DEFAULT_CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Default limit config not found"));
        OffsetDateTime now = OffsetDateTime.now();
        UserLimit userLimit = new UserLimit();
        userLimit.setUserId(userId);
        userLimit.setAvailableAmount(config.getDefaultLimit());
        userLimit.setCreatedAt(now);
        userLimit.setUpdatedAt(now);

        int inserted = userLimitRepository.insertIfAbsent(
                userLimit.getUserId(),
                userLimit.getAvailableAmount(),
                userLimit.getCreatedAt(),
                userLimit.getUpdatedAt()
        );
        if (inserted == 1) {
            return userLimit;
        }

        return userLimitRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("User not found after concurrent create: " + userId));
    }

    private LimitOperation getOperationForUpdate(String operationId) {
        return limitOperationRepository.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new OperationNotFoundException("Operation not found: " + operationId));
    }

    private ReservationCommandResult toCommandResult(LimitOperation operation, boolean created) {
        UserLimit userLimit = userLimitRepository.findById(operation.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for operation " + operation.getOperationId()));

        return new ReservationCommandResult(
                operation.getOperationId(),
                operation.getUserId(),
                operation.getAmount(),
                operation.getStatus(),
                userLimit.getAvailableAmount(),
                created
        );
    }
}
