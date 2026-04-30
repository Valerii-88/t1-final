package ru.t1.limitservice.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import ru.t1.limitservice.entity.OperationStatus;
import ru.t1.limitservice.error.GlobalExceptionHandler;
import ru.t1.limitservice.error.OperationNotFoundException;
import ru.t1.limitservice.service.LimitQueryService;
import ru.t1.limitservice.service.ReservationCommandResult;
import ru.t1.limitservice.service.ReservationService;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LimitControllerLifecycleTest {

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
    void confirmReturnsOk() throws Exception {
        when(reservationService.confirm("op-1")).thenReturn(new ReservationCommandResult(
                "op-1",
                "user-1",
                new BigDecimal("250.00"),
                OperationStatus.CONFIRMED,
                new BigDecimal("750.00"),
                false
        ));

        mockMvc.perform(post("/api/v1/limits/reservations/op-1/confirm").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("op-1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.availableAmount").value(750.00));
    }

    @Test
    void cancelReturnsOk() throws Exception {
        when(reservationService.cancel("op-2")).thenReturn(new ReservationCommandResult(
                "op-2",
                "user-1",
                new BigDecimal("250.00"),
                OperationStatus.CANCELED,
                new BigDecimal("1000.00"),
                false
        ));

        mockMvc.perform(post("/api/v1/limits/reservations/op-2/cancel").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value("op-2"))
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.availableAmount").value(1000.00));
    }

    @Test
    void confirmReturnsNotFoundForMissingOperation() throws Exception {
        when(reservationService.confirm("missing")).thenThrow(new OperationNotFoundException("Operation not found: missing"));

        mockMvc.perform(post("/api/v1/limits/reservations/missing/confirm")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Operation not found: missing"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/reservations/missing/confirm"));
    }

    @Test
    void confirmReturnsValidationErrorWhenOperationIdExceedsDatabaseLength() throws Exception {
        String tooLongOperationId = "o".repeat(129);

        mockMvc.perform(post("/api/v1/limits/reservations/{operationId}/confirm", tooLongOperationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("operationId must not exceed 128 characters"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/reservations/" + tooLongOperationId + "/confirm"));
    }
}
