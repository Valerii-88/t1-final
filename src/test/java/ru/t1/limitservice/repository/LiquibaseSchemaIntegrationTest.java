package ru.t1.limitservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.t1.limitservice.LimitServiceApplication;
import ru.t1.limitservice.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LimitServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class LiquibaseSchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseCreatesExpectedTablesAndSeedsDefaultConfig() {
        Set<String> tableNames = Set.copyOf(jdbcTemplate.queryForList(
                """
                        select table_name
                        from information_schema.tables
                        where table_schema = 'public'
                          and table_name in ('limit_config', 'user_limit', 'limit_operation')
                        """,
                String.class
        ));

        assertThat(tableNames).containsExactlyInAnyOrder("limit_config", "user_limit", "limit_operation");

        BigDecimal defaultLimit = jdbcTemplate.queryForObject(
                "select default_limit from limit_config where id = 1",
                BigDecimal.class
        );

        assertThat(defaultLimit).isEqualByComparingTo("100000.00");

        assertThat(columnIsNullable("limit_config", "created_at")).isFalse();
        assertThat(columnIsNullable("limit_config", "updated_at")).isFalse();
        assertThat(columnIsNullable("user_limit", "created_at")).isFalse();
        assertThat(columnIsNullable("user_limit", "updated_at")).isFalse();
        assertThat(columnIsNullable("limit_operation", "created_at")).isFalse();

        Set<String> indexDefinitions = Set.copyOf(jdbcTemplate.queryForList(
                """
                        select indexdef
                        from pg_indexes
                        where schemaname = 'public'
                          and tablename = 'limit_operation'
                        """,
                String.class
        ));

        assertThat(indexDefinitions)
                .anySatisfy(indexDefinition -> {
                    assertThat(indexDefinition).contains("idx_limit_operation_user_id");
                    assertThat(indexDefinition).contains("(user_id)");
                })
                .anySatisfy(indexDefinition -> {
                    assertThat(indexDefinition).contains("idx_limit_operation_status");
                    assertThat(indexDefinition).contains("(status)");
                });

        Set<String> foreignKeys = Set.copyOf(jdbcTemplate.queryForList(
                """
                        select tc.constraint_name
                        from information_schema.table_constraints tc
                        join information_schema.key_column_usage kcu
                          on tc.constraint_name = kcu.constraint_name
                         and tc.table_schema = kcu.table_schema
                        join information_schema.constraint_column_usage ccu
                          on tc.constraint_name = ccu.constraint_name
                         and tc.table_schema = ccu.table_schema
                        where tc.table_schema = 'public'
                          and tc.table_name = 'limit_operation'
                          and tc.constraint_type = 'FOREIGN KEY'
                          and kcu.column_name = 'user_id'
                          and ccu.table_name = 'user_limit'
                          and ccu.column_name = 'user_id'
                        """,
                String.class
        ));

        assertThat(foreignKeys).isNotEmpty();
    }

    private boolean columnIsNullable(String tableName, String columnName) {
        String isNullable = jdbcTemplate.queryForObject(
                """
                        select is_nullable
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = ?
                          and column_name = ?
                        """,
                String.class,
                tableName,
                columnName
        );

        return "YES".equals(isNullable);
    }
}
