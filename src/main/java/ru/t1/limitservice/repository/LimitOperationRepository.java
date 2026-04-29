package ru.t1.limitservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.t1.limitservice.entity.LimitOperation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface LimitOperationRepository extends JpaRepository<LimitOperation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from LimitOperation o where o.operationId = :operationId")
    Optional<LimitOperation> findByOperationIdForUpdate(@Param("operationId") String operationId);

    @Modifying
    @Transactional
    @Query(
            value = """
                    insert into limit_operation (operation_id, user_id, amount, status, created_at)
                    values (:operationId, :userId, :amount, :status, :createdAt)
                    on conflict (operation_id) do nothing
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("operationId") String operationId,
            @Param("userId") String userId,
            @Param("amount") BigDecimal amount,
            @Param("status") String status,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Modifying
    @Transactional
    @Query("""
            update LimitOperation o
            set o.status = ru.t1.limitservice.entity.OperationStatus.CANCELED,
                o.canceledAt = :canceledAt
            where o.status = ru.t1.limitservice.entity.OperationStatus.RESERVED
            """)
    int cancelReservedOperations(@Param("canceledAt") OffsetDateTime canceledAt);
}
