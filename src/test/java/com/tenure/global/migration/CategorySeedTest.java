package com.tenure.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.support.IntegrationTestSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code V2__seed_category.sql} 검증.
 *
 * <p>카테고리는 {@code item.category_id} 가 참조하는 마스터 데이터라 id 가 환경마다 달라지면
 * 안 된다. 개수·계층·id 고정 여부를 함께 확인한다.
 */
@DisplayName("카테고리 시드")
class CategorySeedTest extends IntegrationTestSupport {

	/** 피그마 '세부 카테고리 자세히 확인'(2825:38923) 기준. */
	private static final int EXPECTED_MAJOR = 10;
	private static final int EXPECTED_DETAIL = 102;

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("대분류 10종이 화면 순서대로 들어간다")
	void seed_applied_insertsMajorCategoriesInScreenOrder() throws Exception {
		List<String> majors = queryStrings(
				"select name from category where depth = 1 order by sort_order");

		assertThat(majors).containsExactly(
				"아우터", "상의", "하의", "신발", "가방", "모자", "치마", "원피스", "액세서리", "쥬얼리");
		assertThat(majors).hasSize(EXPECTED_MAJOR);
	}

	@Test
	@DisplayName("세부 카테고리 102종이 대분류별 개수대로 들어간다")
	void seed_applied_insertsDetailCategoriesPerMajor() throws Exception {
		assertThat(queryLong("select count(*) from category where depth = 2"))
				.isEqualTo(EXPECTED_DETAIL);

		List<String> counts = queryStrings("""
				select concat(p.name, '=', count(*))
				  from category c join category p on p.id = c.parent_id
				 where c.depth = 2
				 group by p.id, p.name, p.sort_order
				 order by p.sort_order""");

		assertThat(counts).containsExactly(
				"아우터=14", "상의=12", "하의=11", "신발=8", "가방=10",
				"모자=9", "치마=11", "원피스=9", "액세서리=9", "쥬얼리=9");
	}

	@Test
	@DisplayName("depth 2 는 전부 부모를 갖고 depth 1 은 전부 부모가 없다")
	void seed_applied_keepsTwoLevelHierarchy() throws Exception {
		assertThat(queryLong("select count(*) from category where depth = 1 and parent_id is not null"))
				.isZero();
		assertThat(queryLong("select count(*) from category where depth = 2 and parent_id is null"))
				.isZero();
		// 2단 고정 — 손자 카테고리가 생기면 화면의 2단 드롭다운이 깨진다
		assertThat(queryLong("""
				select count(*) from category c
				  join category p on p.id = c.parent_id
				 where p.depth <> 1"""))
				.isZero();
	}

	@Test
	@DisplayName("id 가 고정값이라 환경 간 category_id 가 어긋나지 않는다")
	void seed_applied_usesFixedIds() throws Exception {
		assertThat(queryLong("select id from category where depth = 1 and sort_order = 1")).isEqualTo(1);
		assertThat(queryLong("select min(id) from category where depth = 2")).isEqualTo(101);
		assertThat(queryLong("select max(id) from category where depth = 2")).isEqualTo(202);
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
		try (Connection c = dataSource.getConnection();
				Statement s = c.createStatement();
				ResultSet rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}
}
