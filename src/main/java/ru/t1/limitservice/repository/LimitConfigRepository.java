package ru.t1.limitservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.t1.limitservice.entity.LimitConfig;

public interface LimitConfigRepository extends JpaRepository<LimitConfig, Long> {
}
