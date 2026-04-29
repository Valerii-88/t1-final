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
import ru.t1.limitservice.api.LimitResponse;
import ru.t1.limitservice.error.GlobalExceptionHandler;
import ru.t1.limitservice.service.LimitQueryService;
import ru.t1.limitservice.service.ReservationService;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LimitControllerGetTest {

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
    void getLimitReturnsPayload() throws Exception {
        when(limitQueryService.getLimit("user-42")).thenReturn(new LimitResponse(
                "user-42",
                new BigDecimal("75000.00"),
                new BigDecimal("100000.00"),
                OffsetDateTime.parse("2026-04-29T10:15:30Z")
        ));

        mockMvc.perform(get("/api/v1/limits/user-42").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.availableAmount").value(75000.00))
                .andExpect(jsonPath("$.defaultAmount").value(100000.00))
                .andExpect(jsonPath("$.updatedAt").value("2026-04-29T10:15:30Z"));
    }

    @Test
    void getLimitReturnsValidationErrorWhenUserIdExceedsDatabaseLength() throws Exception {
        String tooLongUserId = "u".repeat(129);

        mockMvc.perform(get("/api/v1/limits/{userId}", tooLongUserId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("userId must not exceed 128 characters"))
                .andExpect(jsonPath("$.path").value("/api/v1/limits/" + tooLongUserId));
    }
}
