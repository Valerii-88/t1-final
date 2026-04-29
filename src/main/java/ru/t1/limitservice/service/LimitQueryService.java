package ru.t1.limitservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.t1.limitservice.api.LimitResponse;
import ru.t1.limitservice.entity.LimitConfig;
import ru.t1.limitservice.entity.UserLimit;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import java.time.OffsetDateTime;

@Service
public class LimitQueryService {

    private static final long DEFAULT_CONFIG_ID = 1L;

    private final UserLimitRepository userLimitRepository;
    private final LimitConfigRepository limitConfigRepository;

    public LimitQueryService(UserLimitRepository userLimitRepository, LimitConfigRepository limitConfigRepository) {
        this.userLimitRepository = userLimitRepository;
        this.limitConfigRepository = limitConfigRepository;
    }

    @Transactional
    public LimitResponse getLimit(String userId) {
        LimitConfig config = getDefaultConfig();
        UserLimit userLimit = userLimitRepository.findById(userId)
                .orElseGet(() -> createUser(userId, config));

        return new LimitResponse(
                userLimit.getUserId(),
                userLimit.getAvailableAmount(),
                config.getDefaultLimit(),
                userLimit.getUpdatedAt()
        );
    }

    private LimitConfig getDefaultConfig() {
        return limitConfigRepository.findById(DEFAULT_CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Default limit config not found"));
    }

    private UserLimit createUser(String userId, LimitConfig config) {
        OffsetDateTime now = OffsetDateTime.now();
        UserLimit userLimit = new UserLimit();
        userLimit.setUserId(userId);
        userLimit.setAvailableAmount(config.getDefaultLimit());
        userLimit.setCreatedAt(now);
        userLimit.setUpdatedAt(now);

        int inserted = userLimitRepository.insertIfAbsent(
                userLimit.getUserId(),
                userLimit.getAvailableAmount(),
                userLimit.getCreatedAt(),
                userLimit.getUpdatedAt()
        );
        if (inserted == 1) {
            return userLimit;
        }

        return userLimitRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found after concurrent create: " + userId));
    }
}
