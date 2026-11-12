package com.staybook;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 완료 기준: 빈 DB에서 V1부터 V5까지 Flyway 마이그레이션이 오류 없이 적용된다.
 * <p>
 * 개발 DB를 비울 수는 없으므로 검증 전용 스키마({DB_NAME}_migration_test)를 매번 비우고 처음부터 적용한다.
 * 이 스키마는 한 번 만들어 두어야 한다 (MySQL root로 실행):
 * <pre>
 * CREATE DATABASE {DB_NAME}_migration_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 * GRANT ALL PRIVILEGES ON {DB_NAME}_migration_test.* TO '{DB_USERNAME}'@'%';
 * </pre>
 * 스키마에 접속할 수 없으면 테스트를 건너뛴다.
 */
class FlywayMigrationTest {

    private static final String SCHEMA_SUFFIX = "_migration_test";

    private final String url = "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306") + "/"
            + env("DB_NAME", "staybook") + SCHEMA_SUFFIX + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
    private final String username = env("DB_USERNAME", "");
    private final String password = env("DB_PASSWORD", "");

    @Test
    void 빈_DB에서_V1부터_V5까지_오류_없이_적용된다() throws Exception {
        assumeSchemaIsReachable();
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(false)   // 검증 전용 스키마에서만 쓴다
                .load();
        flyway.clean();
        assertThat(count("select count(*) from information_schema.tables where table_schema = database()"))
                .as("시작할 때 스키마는 비어 있어야 한다").isZero();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(5);
        assertThat(result.targetSchemaVersion).isEqualTo("5");
        assertThat(count("select count(*) from flyway_schema_history where success = 1")).isEqualTo(5);

        // ERD의 30개 테이블
        assertThat(count("select count(*) from information_schema.tables where table_schema = database() "
                + "and table_name <> 'flyway_schema_history'")).isEqualTo(30);
        // V3에서 미뤘던 FK가 V4에서 추가됐는지
        assertThat(count("select count(*) from information_schema.referential_constraints "
                + "where constraint_schema = database() and constraint_name = 'fk_inventory_holds_reservation'"))
                .isEqualTo(1);
        // 마이그레이션으로 넣는 초기 데이터
        assertThat(count("select count(*) from terms")).isEqualTo(3);
        assertThat(count("select count(*) from terms where required = true")).isEqualTo(2);
        assertThat(count("select count(*) from regions where level = 1")).isEqualTo(17);
        assertThat(count("select count(*) from regions where level = 2 and parent_id is not null")).isEqualTo(17);
        assertThat(count("select count(*) from amenities")).isEqualTo(10);
        // 리프레시 토큰, Spotify 트랙 테이블은 만들지 않는다
        assertThat(count("select count(*) from information_schema.tables where table_schema = database() "
                + "and (table_name like '%refresh%' or table_name like '%spotify%' or table_name like '%token%' "
                + "and table_name <> 'password_reset_tokens')")).isZero();

        // 다시 실행하면 적용할 것이 없다
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private void assumeSchemaIsReachable() {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            // 접속되면 진행한다
        } catch (SQLException e) {
            Assumptions.abort("검증 전용 스키마에 접속할 수 없어 건너뜁니다. 클래스 주석의 SQL로 스키마를 만들어 주세요: "
                    + e.getMessage());
        }
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
