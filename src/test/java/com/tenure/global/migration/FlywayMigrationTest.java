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
 * V1__init.sql 이 실제 MySQL 8 에 적용되는지 검증한다.
 *
 * DDL 은 docs/schema.yaml 에서 생성한다. 생성기 결과가 문법만 맞고 제약이 빠져
 * 있을 수 있으므로, 적용된 뒤 information_schema 를 세어 정본과 대조한다.
 * 컨텍스트를 띄워 검증하므로 운영과 같은 경로(Spring 이 Flyway 를 구동)를 탄다.
 */
@DisplayName("Flyway V1 마이그레이션")
class FlywayMigrationTest extends IntegrationTestSupport {

	/** 정본 docs/schema.yaml 기준 수치. 스키마를 바꾸면 이 숫자도 같이 바뀐다. */
	private static final int EXPECTED_TABLES = 39;
	// V17 기준 — V16 에서 순증 1(DEC-026), V17 에서 category.measurement_scheme 이 늘어 다시 +1
	private static final int EXPECTED_COLUMNS = 322;
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
		// 인증번호·토큰은 Redis 로 옮겼다 (DEC-009). V3 가 내린다
		assertThat(tables).doesNotContain("email_verification");
		// 최근 열람 이력은 Redis Sorted Set 으로 옮겼다 (DEC-014). V4 가 내린다
		assertThat(tables).doesNotContain("recent_view");
		// 사이즈 칩은 프론트 하드코딩이 아니라 데이터다 (Q6 해소). V15 가 만든다
		assertThat(tables).contains("size_option");
	}

	@Test
	@DisplayName("컬럼 320개와 FK 69개가 만들어진다")
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
	@DisplayName("기본 배송지를 두 개 만들면 유니크 인덱스가 막는다")
	void shippingAddress_secondDefault_rejectedByUniqueIndex() throws Exception {
		// 애플리케이션이 '기존 기본을 내리고 새 것을 올린다' 순서로 지키지만, 동시에 들어오면
		// 둘 다 true 가 될 수 있다. MySQL 은 부분 유니크가 없어 함수 인덱스로 막는다
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("""
						insert into `user` (email, password_hash, real_name, phone, nickname,
						                    grade, status, follower_count, post_count, created_at, updated_at)
						 values ('default-address-test@tenure.local', 'x', '홍길동', '01011112222',
						         'default-address-test', 'BASIC', 'ACTIVE', 0, 0, now(6), now(6))""");
				long userId = queryLong(statement, "select last_insert_id()");

				statement.execute(insertAddress(userId, "07027", true));
				// 기본이 아닌 배송지는 몇 개든 쌓인다 — 인덱스 값이 NULL 이라 중복으로 보지 않는다
				statement.execute(insertAddress(userId, "06978", false));
				statement.execute(insertAddress(userId, "07040", false));

				assertThatInsertFails(statement, insertAddress(userId, "06158", true));
			} finally {
				connection.rollback();
			}
		}
	}

	private static String insertAddress(long userId, String zipcode, boolean isDefault) {
		return """
				insert into shipping_address (user_id, recipient_name, phone, zipcode, address,
				                              is_default, created_at, updated_at)
				 values (%d, '홍길동', '010-1234-5678', '%s', '서울특별시 동작구 상도로 369',
				         %b, now(6), now(6))""".formatted(userId, zipcode, isDefault);
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
