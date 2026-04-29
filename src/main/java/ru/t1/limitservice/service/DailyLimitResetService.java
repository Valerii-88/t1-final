package ru.t1.limitservice.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.t1.limitservice.entity.LimitConfig;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class DailyLimitResetService {

    private static final long DEFAULT_CONFIG_ID = 1L;

    private final LimitConfigRepository limitConfigRepository;
    private final LimitOperationRepository limitOperationRepository;
    private final UserLimitRepository userLimitRepository;
    private final Clock clock;

    public DailyLimitResetService(
            LimitConfigRepository limitConfigRepository,
            LimitOperationRepository limitOperationRepository,
            UserLimitRepository userLimitRepository,
            Clock clock
    ) {
        this.limitConfigRepository = limitConfigRepository;
        this.limitOperationRepository = limitOperationRepository;
        this.userLimitRepository = userLimitRepository;
        this.clock = clock;
    }

    @Transactional
    @Scheduled(cron = "0 0 0 * * *", zone = "${app.limit.reset-zone:Europe/Moscow}")
    public void resetDailyLimits() {
        LimitConfig config = limitConfigRepository.findById(DEFAULT_CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Default limit config not found"));
        OffsetDateTime now = OffsetDateTime.now(clock);

        limitOperationRepository.cancelReservedOperations(now);
        userLimitRepository.resetAllAvailableAmounts(config.getDefaultLimit(), now);
    }
}
