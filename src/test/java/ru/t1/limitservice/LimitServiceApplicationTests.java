package ru.t1.limitservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.mockito.Mockito;
import ru.t1.limitservice.repository.LimitConfigRepository;
import ru.t1.limitservice.repository.LimitOperationRepository;
import ru.t1.limitservice.repository.UserLimitRepository;

import static org.assertj.core.api.Assertions.assertThat;

class LimitServiceApplicationTests {

    @Test
    void applicationStartsWithoutLiveDatabaseConnection() throws Exception {
        Class<?> applicationClass = Class.forName("ru.t1.limitservice.LimitServiceApplication");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(applicationClass, NoDatabaseBeansConfig.class)
                .properties(
                        "spring.autoconfigure.exclude=" + String.join(",",
                                DataSourceAutoConfiguration.class.getName(),
                                HibernateJpaAutoConfiguration.class.getName(),
                                LiquibaseAutoConfiguration.class.getName()
                        ),
                        "spring.main.lazy-initialization=true",
                        "spring.main.web-application-type=none",
                        "spring.task.scheduling.enabled=false"
                )
                .run()) {
            assertThat(context.isActive()).isTrue();
        }
    }

    @TestConfiguration
    static class NoDatabaseBeansConfig {

        @Bean
        LimitConfigRepository limitConfigRepository() {
            return Mockito.mock(LimitConfigRepository.class);
        }

        @Bean
        UserLimitRepository userLimitRepository() {
            return Mockito.mock(UserLimitRepository.class);
        }

        @Bean
        LimitOperationRepository limitOperationRepository() {
            return Mockito.mock(LimitOperationRepository.class);
        }
    }
}
