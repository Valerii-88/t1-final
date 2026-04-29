package ru.t1.limitservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.t1.limitservice.LimitServiceApplication;
import ru.t1.limitservice.api.ReservationRequest;
import ru.t1.limitservice.entity.LimitOperation;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;
import ru.t1.limitservice.support.PostgresIntegrationTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LimitServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ReservationServicePersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ReservationService reservationService;

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
    void reserveAutoCreatesUserAndConfirmPreservesDebitedBalance() {
        ReservationCommandResult reserveResult = reservationService.reserve(
                new ReservationRequest("op-db-1", "user-db-1", new BigDecimal("250.00"))
        );

        assertThat(reserveResult.created()).isTrue();
        assertThat(reserveResult.status()).isEqualTo(OperationStatus.RESERVED);
        assertThat(reserveResult.availableAmount()).isEqualByComparingTo("99750.00");

        UserLimit persistedUserAfterReserve = userLimitRepository.findById("user-db-1").orElseThrow();
        LimitOperation persistedOperationAfterReserve = limitOperationRepository.findById("op-db-1").orElseThrow();

        assertThat(persistedUserAfterReserve.getAvailableAmount()).isEqualByComparingTo("99750.00");
        assertThat(persistedOperationAfterReserve.getStatus()).isEqualTo(OperationStatus.RESERVED);

        ReservationCommandResult confirmResult = reservationService.confirm("op-db-1");

        assertThat(confirmResult.status()).isEqualTo(OperationStatus.CONFIRMED);
        assertThat(confirmResult.availableAmount()).isEqualByComparingTo("99750.00");

        UserLimit persistedUserAfterConfirm = userLimitRepository.findById("user-db-1").orElseThrow();
        LimitOperation persistedOperationAfterConfirm = limitOperationRepository.findById("op-db-1").orElseThrow();

        assertThat(persistedUserAfterConfirm.getAvailableAmount()).isEqualByComparingTo("99750.00");
        assertThat(persistedOperationAfterConfirm.getStatus()).isEqualTo(OperationStatus.CONFIRMED);
        assertThat(persistedOperationAfterConfirm.getConfirmedAt()).isNotNull();
    }

    @Test
    void reserveAndCancelRestoresAvailableBalance() {
        ReservationCommandResult reserveResult = reservationService.reserve(
                new ReservationRequest("op-db-2", "user-db-2", new BigDecimal("125.00"))
        );

        assertThat(reserveResult.status()).isEqualTo(OperationStatus.RESERVED);
        assertThat(reserveResult.availableAmount()).isEqualByComparingTo("99875.00");

        ReservationCommandResult cancelResult = reservationService.cancel("op-db-2");

        assertThat(cancelResult.status()).isEqualTo(OperationStatus.CANCELED);
        assertThat(cancelResult.availableAmount()).isEqualByComparingTo("100000.00");

        UserLimit persistedUserAfterCancel = userLimitRepository.findById("user-db-2").orElseThrow();
        LimitOperation persistedOperationAfterCancel = limitOperationRepository.findById("op-db-2").orElseThrow();

        assertThat(persistedUserAfterCancel.getAvailableAmount()).isEqualByComparingTo("100000.00");
        assertThat(persistedOperationAfterCancel.getStatus()).isEqualTo(OperationStatus.CANCELED);
        assertThat(persistedOperationAfterCancel.getCanceledAt()).isNotNull();
    }
}
