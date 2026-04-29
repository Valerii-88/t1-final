package ru.t1.limitservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.error.GlobalExceptionHandler;
import ru.t1.limitservice.error.InsufficientLimitException;
import ru.t1.limitservice.service.LimitQueryService;
import ru.t1.limitservice.service.ReservationCommandResult;
import ru.t1.limitservice.service.ReservationService;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LimitControllerReserveTest {

    @Mock
    private LimitQueryService limitQueryService;

    @Mock
    private ReservationService reservationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new LimitController(limitQueryService, reservationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        JsonMapper.builder()
                                .findAndAddModules()
                                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                .build()
                ))
                .build();
    }

    @Test
    void reserveReturnsCreatedForNewOperation() throws Exception {
        when(reservationService.reserve(any())).thenReturn(new ReservationCommandResult(
                "op-1",
                "user-1",
                new BigDecimal("250.00"),
                OperationStatus.RESERVED,
                new BigDecimal("750.00"),
                true
        ));

        mockMvc.perform(post("/api/v1/limits/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-1",
                                  "userId": "user-1",
                                  "amount": 250.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operationId").value("op-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.availableAmount").value(750.00));
    }

    @Test
    void reserveReturnsTooManyRequestsWhenAvailableAmountIsInsufficient() throws Exception {
        when(reservationService.reserve(any())).thenThrow(new InsufficientLimitException("Not enough limit for user-1"));

        mockMvc.perform(post("/api/v1/limits/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-2",
                                  "userId": "user-1",
                                  "amount": 250.00
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_LIMIT"))
                .andExpect(jsonPath("$.message").value("Not enough limit for user-1"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/reservations"));
    }

    @Test
    void reserveReturnsValidationErrorForInvalidAmountScale() throws Exception {
        mockMvc.perform(post("/api/v1/limits/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-3",
                                  "userId": "user-1",
                                  "amount": 10.123
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("amount must have up to 2 decimal places"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/reservations"));
    }

    @Test
    void reserveReturnsValidationErrorWhenOperationIdExceedsDatabaseLength() throws Exception {
        String tooLongOperationId = "o".repeat(129);

        mockMvc.perform(post("/api/v1/limits/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "%s",
                                  "userId": "user-1",
                                  "amount": 10.00
                                }
                                """.formatted(tooLongOperationId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("operationId must not exceed 128 characters"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/reservations"));
    }

    @Test
    void reserveReturnsValidationErrorWhenUserIdExceedsDatabaseLength() throws Exception {
        String tooLongUserId = "u".repeat(129);

        mockMvc.perform(post("/api/v1/limits/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "op-4",
                                  "userId": "%s",
                                  "amount": 10.00
                                }
                                """.formatted(tooLongUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("userId must not exceed 128 characters"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/reservations"));
    }
}
