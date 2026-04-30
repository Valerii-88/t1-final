package ru.t1.limitservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.t1.limitservice.LimitServiceApplication;
import ru.t1.limitservice.entity.LimitOperation;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;
import ru.t1.limitservice.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LimitServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DailyLimitResetServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private DailyLimitResetService dailyLimitResetService;

    @Autowired
    private UserLimitRepository userLimitRepository;

    @Autowired
    private LimitOperationRepository limitOperationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("delete from limit_operation");
        jdbcTemplate.update("delete from user_limit");
    }

    @Test
    void resetCancelsReservedOperationsAndRestoresDefaultBalance() {
        UserLimit reservedUser = new UserLimit();
        reservedUser.setUserId("reset-user-1");
        reservedUser.setAvailableAmount(new BigDecimal("99750.00"));
        reservedUser.setCreatedAt(OffsetDateTime.now().minusHours(1));
        reservedUser.setUpdatedAt(OffsetDateTime.now().minusMinutes(30));
        userLimitRepository.saveAndFlush(reservedUser);

        LimitOperation reservedOperation = new LimitOperation();
        reservedOperation.setOperationId("reset-op-1");
        reservedOperation.setUserId("reset-user-1");
        reservedOperation.setAmount(new BigDecimal("250.00"));
        reservedOperation.setStatus(OperationStatus.RESERVED);
        reservedOperation.setCreatedAt(OffsetDateTime.now().minusMinutes(20));
        limitOperationRepository.saveAndFlush(reservedOperation);

        UserLimit confirmedUser = new UserLimit();
        confirmedUser.setUserId("reset-user-2");
        confirmedUser.setAvailableAmount(new BigDecimal("99500.00"));
        confirmedUser.setCreatedAt(OffsetDateTime.now().minusHours(1));
        confirmedUser.setUpdatedAt(OffsetDateTime.now().minusMinutes(30));
        userLimitRepository.saveAndFlush(confirmedUser);

        LimitOperation confirmedOperation = new LimitOperation();
        confirmedOperation.setOperationId("reset-op-2");
        confirmedOperation.setUserId("reset-user-2");
        confirmedOperation.setAmount(new BigDecimal("500.00"));
        confirmedOperation.setStatus(OperationStatus.CONFIRMED);
        confirmedOperation.setCreatedAt(OffsetDateTime.now().minusMinutes(20));
        confirmedOperation.setConfirmedAt(OffsetDateTime.now().minusMinutes(10));
        limitOperationRepository.saveAndFlush(confirmedOperation);

        dailyLimitResetService.resetDailyLimits();

        UserLimit persistedReservedUser = userLimitRepository.findById("reset-user-1").orElseThrow();
        LimitOperation persistedReservedOperation = limitOperationRepository.findById("reset-op-1").orElseThrow();
        UserLimit persistedConfirmedUser = userLimitRepository.findById("reset-user-2").orElseThrow();
        LimitOperation persistedConfirmedOperation = limitOperationRepository.findById("reset-op-2").orElseThrow();

        assertThat(persistedReservedUser.getAvailableAmount()).isEqualByComparingTo("100000.00");
        assertThat(persistedReservedOperation.getStatus()).isEqualTo(OperationStatus.CANCELED);
        assertThat(persistedReservedOperation.getCanceledAt()).isNotNull();

        assertThat(persistedConfirmedUser.getAvailableAmount()).isEqualByComparingTo("100000.00");
        assertThat(persistedConfirmedOperation.getStatus()).isEqualTo(OperationStatus.CONFIRMED);
        assertThat(persistedConfirmedOperation.getConfirmedAt()).isNotNull();
    }
}
