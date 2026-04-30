package ru.t1.limitservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.t1.limitservice.api.LimitResponse;
import ru.t1.limitservice.entity.LimitConfig;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimitQueryServiceTest {

    @Mock
    private UserLimitRepository userLimitRepository;

    @Mock
    private LimitConfigRepository limitConfigRepository;

    @InjectMocks
    private LimitQueryService limitQueryService;

    @Test
    void getLimitAutoCreatesUserWithDefaultConfigWhenMissing() {
        LimitConfig config = limitConfig(new BigDecimal("100000.00"));
        when(limitConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(userLimitRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userLimitRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(1);

        LimitResponse response = limitQueryService.getLimit("user-1");

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.availableAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.defaultAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.updatedAt()).isNotNull();
        verify(userLimitRepository).insertIfAbsent(any(), any(), any(), any());
        verify(userLimitRepository, times(0)).saveAndFlush(any(UserLimit.class));
    }

    @Test
    void getLimitReturnsExistingUserPayload() {
        LimitConfig config = limitConfig(new BigDecimal("100000.00"));
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-04-29T10:15:30Z");
        UserLimit userLimit = userLimit("user-42", new BigDecimal("75000.00"), updatedAt.minusDays(1), updatedAt);

        when(limitConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(userLimitRepository.findById("user-42")).thenReturn(Optional.of(userLimit));

        LimitResponse response = limitQueryService.getLimit("user-42");

        assertThat(response.userId()).isEqualTo("user-42");
        assertThat(response.availableAmount()).isEqualByComparingTo("75000.00");
        assertThat(response.defaultAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        verify(userLimitRepository, times(0)).saveAndFlush(any(UserLimit.class));
    }

    @Test
    void getLimitReturnsExistingUserWhenInsertIfAbsentLosesCreationRace() {
        LimitConfig config = limitConfig(new BigDecimal("100000.00"));
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-04-29T10:15:30Z");
        UserLimit existingUser = userLimit("user-77", new BigDecimal("100000.00"), updatedAt.minusDays(1), updatedAt);

        when(limitConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(userLimitRepository.findById("user-77")).thenReturn(Optional.empty(), Optional.of(existingUser));
        when(userLimitRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);

        LimitResponse response = limitQueryService.getLimit("user-77");

        assertThat(response.userId()).isEqualTo("user-77");
        assertThat(response.availableAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.defaultAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        verify(userLimitRepository, times(0)).saveAndFlush(any(UserLimit.class));
    }

    private static LimitConfig limitConfig(BigDecimal defaultAmount) {
        LimitConfig config = new LimitConfig();
        config.setId(1L);
        config.setDefaultLimit(defaultAmount);
        config.setCreatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        config.setUpdatedAt(OffsetDateTime.parse("2026-04-29T00:00:00Z"));
        return config;
    }

    private static UserLimit userLimit(
            String userId,
            BigDecimal availableAmount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        UserLimit userLimit = new UserLimit();
        userLimit.setUserId(userId);
        userLimit.setAvailableAmount(availableAmount);
        userLimit.setCreatedAt(createdAt);
        userLimit.setUpdatedAt(updatedAt);
        return userLimit;
    }
}
