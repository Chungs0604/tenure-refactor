package com.tenure.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.support.IntegrationTestSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code V1__init.sql} 이 실제 MySQL 8 에 적용되는지 검증한다.
 *
 * <p>DDL 은 {@code docs/schema.yaml} 에서 생성한다. 생성기 결과가 문법만 맞고 제약이 빠져
 * 있을 수 있으므로, 적용된 뒤 {@code information_schema} 를 세어 정본과 대조한다.
 * 컨텍스트를 띄워 검증하므로 운영과 같은 경로(Spring 이 Flyway 를 구동)를 탄다.
 */
@DisplayName("Flyway V1 마이그레이션")
class FlywayMigrationTest extends IntegrationTestSupport {

	/** 정본 {@code docs/schema.yaml} 기준 수치. 스키마를 바꾸면 이 숫자도 같이 바뀐다. */
	private static final int EXPECTED_TABLES = 39;
	private static final int EXPECTED_COLUMNS = 317;
	private static final int EXPECTED_FOREIGN_KEYS = 69;

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("테이블 39개가 생성된다")
	void migration_applied_createsAllTables() throws Exception {
		List<String> tables = queryStrings("""
				select table_name from information_schema.tables
				 where table_schema = database() and table_name <> 'flyway_schema_history'
				 order by table_name""");

		assertThat(tables).hasSize(EXPECTED_TABLES);
		// 0순위 테이블이 빠지면 '기록 승계' 가 성립하지 않는다
		assertThat(tables).contains("item_ownership", "item_wear_record", "trade_order", "purchase_offer");
		// 예약어 회피 확인 — order 가 아니라 trade_order 다
		assertThat(tables).doesNotContain("order");
	}

	@Test
	@DisplayName("컬럼 317개와 FK 69개가 만들어진다")
	void migration_applied_createsAllColumnsAndForeignKeys() throws Exception {
		long columns = queryLong("""
				select count(*) from information_schema.columns
				 where table_schema = database() and table_name <> 'flyway_schema_history'""");
		long foreignKeys = queryLong("""
				select count(*) from information_schema.table_constraints
				 where table_schema = database() and constraint_type = 'FOREIGN KEY'""");

		assertThat(columns).isEqualTo(EXPECTED_COLUMNS);
		assertThat(foreignKeys).isEqualTo(EXPECTED_FOREIGN_KEYS);
	}

	@Test
	@DisplayName("모든 테이블이 utf8mb4_0900_ai_ci 를 쓴다")
	void migration_applied_usesExpectedCollation() throws Exception {
		List<String> wrong = queryStrings("""
				select table_name from information_schema.tables
				 where table_schema = database() and table_name <> 'flyway_schema_history'
				   and table_collation <> 'utf8mb4_0900_ai_ci'""");

		assertThat(wrong).isEmpty();
	}

	@Test
	@DisplayName("0순위 테이블을 가리키는 FK 는 전부 RESTRICT 다")
	void migration_applied_zeroPriorityForeignKeysRestrict() throws Exception {
		// CASCADE 로 걸리면 부모가 지워질 때 승계 기록이 통째로 사라진다
		List<String> cascading = queryStrings("""
				select concat(table_name, '.', constraint_name)
				  from information_schema.referential_constraints
				 where constraint_schema = database()
				   and referenced_table_name in
				       ('item', 'post', 'user', 'item_ownership', 'trade_order', 'purchase_offer')
				   and delete_rule <> 'RESTRICT'""");

		assertThat(cascading).isEmpty();
	}

	@Test
	@DisplayName("자기 자신을 팔로우하면 CHECK 제약이 막는다")
	void follow_selfReference_rejectedByCheckConstraint() throws Exception {
		// 컨테이너를 재사용하므로 커밋하지 않는다 — 다른 테스트에 데이터를 남기지 않기 위해
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("""
						insert into `user` (email, password_hash, real_name, phone, nickname,
						                    grade, status, follower_count, post_count, created_at, updated_at)
						 values ('self-follow-test@tenure.local', 'x', '홍길동', '01011112222',
						         'self-follow-test', 'BASIC', 'ACTIVE', 0, 0, now(6), now(6))""");
				long userId = queryLong(statement, "select last_insert_id()");

				assertThatInsertFails(statement,
						"insert into follow (follower_id, following_id, created_at) values (%d, %d, now(6))"
								.formatted(userId, userId));
			} finally {
				connection.rollback();
			}
		}
	}

	private void assertThatInsertFails(Statement statement, String sql) {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> statement.execute(sql))
				.isInstanceOf(SQLException.class);
	}

	private List<String> queryStrings(String sql) throws Exception {
		try (Connection c = dataSource.getConnection();
				Statement s = c.createStatement();
				ResultSet rs = s.executeQuery(sql)) {
			List<String> values = new ArrayList<>();
			while (rs.next()) {
				values.add(rs.getString(1));
			}
			return values;
		}
	}

	private long queryLong(String sql) throws Exception {
		try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
			return queryLong(s, sql);
		}
	}

	private long queryLong(Statement statement, String sql) throws SQLException {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}
}
