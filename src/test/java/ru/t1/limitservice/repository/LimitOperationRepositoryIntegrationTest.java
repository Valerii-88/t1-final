package ru.t1.limitservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.t1.limitservice.LimitServiceApplication;
import ru.t1.limitservice.entity.LimitOperation;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LimitServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class LimitOperationRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private LimitOperationRepository limitOperationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("delete from limit_operation");
        jdbcTemplate.update("delete from user_limit");
        jdbcTemplate.update(
                """
                        insert into user_limit (user_id, available_amount, created_at, updated_at)
                        values ('user-db-op', 100000.00, current_timestamp, current_timestamp)
                        """
        );
    }

    @Test
    void insertIfAbsentReturnsZeroWhenOperationAlreadyExists() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-29T00:00:00Z");

        int firstInsert = limitOperationRepository.insertIfAbsent(
                "op-repo-1",
                "user-db-op",
                new BigDecimal("250.00"),
                OperationStatus.RESERVED.name(),
                createdAt
        );

        int secondInsert = limitOperationRepository.insertIfAbsent(
                "op-repo-1",
                "user-db-op",
                new BigDecimal("999.00"),
                OperationStatus.CANCELED.name(),
                createdAt.plusHours(1)
        );

        LimitOperation persistedOperation = limitOperationRepository.findById("op-repo-1").orElseThrow();

        assertThat(firstInsert).isEqualTo(1);
        assertThat(secondInsert).isZero();
        assertThat(persistedOperation.getUserId()).isEqualTo("user-db-op");
        assertThat(persistedOperation.getAmount()).isEqualByComparingTo("250.00");
        assertThat(persistedOperation.getStatus()).isEqualTo(OperationStatus.RESERVED);
        assertThat(persistedOperation.getCreatedAt()).isEqualTo(createdAt);
    }
}
