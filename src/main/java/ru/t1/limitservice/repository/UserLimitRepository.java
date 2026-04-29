package ru.t1.limitservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.t1.limitservice.entity.UserLimit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface UserLimitRepository extends JpaRepository<UserLimit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserLimit u where u.userId = :userId")
    Optional<UserLimit> findByUserIdForUpdate(@Param("userId") String userId);

    @Modifying
    @Transactional
    @Query(
            value = """
                    insert into user_limit (user_id, available_amount, created_at, updated_at)
                    values (:userId, :availableAmount, :createdAt, :updatedAt)
                    on conflict (user_id) do nothing
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("userId") String userId,
            @Param("availableAmount") BigDecimal availableAmount,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Modifying
    @Transactional
    @Query("update UserLimit u set u.availableAmount = :availableAmount, u.updatedAt = :updatedAt")
    int resetAllAvailableAmounts(
            @Param("availableAmount") BigDecimal availableAmount,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
