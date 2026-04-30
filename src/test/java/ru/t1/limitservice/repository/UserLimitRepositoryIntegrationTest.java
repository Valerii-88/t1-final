package ru.t1.limitservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.t1.limitservice.LimitServiceApplication;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LimitServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class UserLimitRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserLimitRepository userLimitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("delete from limit_operation");
        jdbcTemplate.update("delete from user_limit");
    }

    @Test
    void insertIfAbsentReturnsZeroWhenUserAlreadyExists() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-04-29T00:00:00Z");

        int firstInsert = userLimitRepository.insertIfAbsent(
                "user-db-1",
                new BigDecimal("100000.00"),
                createdAt,
                createdAt
        );

        int secondInsert = userLimitRepository.insertIfAbsent(
                "user-db-1",
                new BigDecimal("50000.00"),
                createdAt.plusHours(1),
                createdAt.plusHours(1)
        );

        UserLimit persistedUser = userLimitRepository.findById("user-db-1").orElseThrow();

        assertThat(firstInsert).isEqualTo(1);
        assertThat(secondInsert).isZero();
        assertThat(persistedUser.getUserId()).isEqualTo("user-db-1");
        assertThat(persistedUser.getAvailableAmount()).isEqualByComparingTo("100000.00");
        assertThat(persistedUser.getCreatedAt()).isEqualTo(createdAt);
        assertThat(persistedUser.getUpdatedAt()).isEqualTo(createdAt);
    }
}
