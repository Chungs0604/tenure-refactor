package com.tenure.domain.search.repository;

import static com.tenure.domain.post.entity.QPost.post;
import static com.tenure.domain.post.entity.QPostItemTag.postItemTag;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tenure.domain.block.repository.BlockPredicates;
import com.tenure.domain.item.entity.QItem;
import com.tenure.domain.item.enums.SaleStatus;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.search.dto.PostSearchCriteria;
import com.tenure.domain.search.enums.AuthorGenderFilter;
import com.tenure.domain.search.enums.PostSortType;
import com.tenure.domain.search.enums.SaleStatusFilter;
import com.tenure.domain.user.enums.Gender;
import com.tenure.global.pagination.Cursor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 게시물 검색(API-200). 키워드는 게시물이 아니라 태그된 아이템과 매칭된다(`post` 에 캡션이
 * 없다) — 그래서 거의 모든 조건이 {@code postItemTag} 를 거쳐 가는 EXISTS 서브쿼리다.
 *
 * 추천 순(RECOMMENDED)의 매칭 등급은 조건(WHERE)과 정렬(ORDER BY) 양쪽에 같은 서브쿼리를
 * 다시 쓴다 — 매칭 안 되는 행을 걷어내는 것과 그 안에서 등급으로 가르는 것이 같은 판정이기
 * 때문이다. 등급을 엔티티 조회 후 Java 에서 다시 매기면 인덱스를 타지 못하고 N+1 이 된다.
 *
 * {@code JpaRepository} 의 Custom 조각이 아니라 독립 빈이다 — item·category·user 를 넘나드는
 * 조회라 PostRepository 하나에 끼워 넣을 수 없다. {@code @Repository} 로 직접 등록한다.
 */
@Repository
@RequiredArgsConstructor
public class PostSearchRepositoryImpl implements PostSearchRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<PostSearchRow> search(PostSearchCriteria criteria, Long viewerId, Cursor cursor, int size) {

		PostSortType sort = criteria.sort() == null ? PostSortType.RECOMMENDED : criteria.sort();

		return switch (sort) {
			case RECOMMENDED -> searchByRecommended(criteria, viewerId, cursor, size);
			case LATEST -> searchByCreatedAt(criteria, viewerId, cursor, size);
			case LIKE, VIEW, SAVE -> searchByCount(sort, criteria, viewerId, cursor, size);
		};
	}

	/** createdAt·likeCount 등은 Post 자체의 컬럼이라, 엔티티만 받아도 Service 가 커서를 다시 만들 수 있다. */
	private List<PostSearchRow> searchByCreatedAt(PostSearchCriteria criteria, Long viewerId, Cursor cursor, int size) {
		List<Post> rows = queryFactory
				.selectFrom(post)
				.where(baseConditions(criteria, viewerId))
				.where(afterCreatedAt(cursor))
				.orderBy(post.createdAt.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();

		return rows.stream()
				.map(p -> new PostSearchRow(p, Cursor.of(p.getCreatedAt(), p.getId())))
				.toList();
	}

	private List<PostSearchRow> searchByCount(
			PostSortType sort, PostSearchCriteria criteria, Long viewerId, Cursor cursor, int size
	) {
		NumberExpression<Integer> sortColumn = sortColumnOf(sort);
		Function<Post, Integer> valueOf = switch (sort) {
			case LIKE -> Post::getLikeCount;
			case VIEW -> Post::getViewCount;
			case SAVE -> Post::getSaveCount;
			case LATEST, RECOMMENDED -> throw new IllegalStateException("호출 쪽에서 이미 분기했어야 함: " + sort);
		};

		List<Post> rows = queryFactory
				.selectFrom(post)
				.where(baseConditions(criteria, viewerId))
				.where(afterCount(sortColumn, cursor))
				.orderBy(sortColumn.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();

		return rows.stream()
				.map(p -> new PostSearchRow(p, Cursor.of(valueOf.apply(p), p.getId())))
				.toList();
	}

	/**
	 * 추천 순은 (매칭 등급, rank_score) 를 합친 값 하나로 커서를 둔다 —
	 * 등급이 1~3, rank_score 가 0~100 미만(DECIMAL(6,4) 상한)이라
	 * {@code grade*100 + rank_score} 가 (grade desc, rank_score desc) 와 정확히 같은 순서를 낸다.
	 * Cursor 가 쓰는 BigDecimal 슬롯 하나로 두 키를 다 담을 수 있는 이유다.
	 *
	 * 이 값은 Post 의 컬럼이 아니라 쿼리 시점 계산값이라 엔티티만으론 못 돌려받는다 — Tuple 로
	 * 같이 select 한다.
	 */
	private List<PostSearchRow> searchByRecommended(PostSearchCriteria criteria, Long viewerId, Cursor cursor, int size) {

		NumberExpression<BigDecimal> combinedKey = matchGrade(criteria.keyword())
				.castToNum(BigDecimal.class)
				.multiply(BigDecimal.valueOf(100))
				.add(post.rankScore);

		List<Tuple> rows = queryFactory
				.select(post, combinedKey)
				.from(post)
				.where(baseConditions(criteria, viewerId))
				.where(afterCombinedKey(combinedKey, cursor))
				.orderBy(combinedKey.desc(), post.id.desc())
				.limit(size + 1L)
				.fetch();

		return rows.stream()
				.map(tuple -> {
					Post p = tuple.get(post);
					BigDecimal key = tuple.get(combinedKey);
					return new PostSearchRow(p, Cursor.of(key, p.getId()));
				})
				.toList();
	}

	@Override
	public long countMatches(PostSearchCriteria criteria, Long viewerId) {
		Long count = queryFactory
				.select(post.id.count())
				.from(post)
				.where(baseConditions(criteria, viewerId))
				.fetchOne();
		return count == null ? 0L : count;
	}

	/** 키워드·필터·차단 제외를 한데 모은다. 정렬 방식과 무관하게 모든 쿼리가 공유한다. */
	private BooleanExpression[] baseConditions(PostSearchCriteria criteria, Long viewerId) {
		List<BooleanExpression> conditions = new ArrayList<>();
		conditions.add(post.deletedAt.isNull());
		conditions.add(BlockPredicates.excludeBlockedEitherWay(post.author.id, viewerId));
		conditions.add(authorGenderCondition(criteria.authorGender()));
		conditions.add(heightCondition(criteria.heightMin(), criteria.heightMax()));
		conditions.add(weightCondition(criteria.weightMin(), criteria.weightMax()));
		conditions.add(saleStatusCondition(criteria.saleStatus()));
		conditions.add(categoryCondition(criteria.categoryIds()));
		conditions.add(keywordMatchCondition(criteria.keyword()));

		return conditions.stream().filter(c -> c != null).toArray(BooleanExpression[]::new);
	}

	/**
	 * 키워드가 있으면 태그된 아이템 중 하나라도 등급 1 이상이어야 한다(등급 0 은 제외).
	 *
	 * 3등급(brandAndNameBothMatch)을 빼면 안 된다 — 2등급(brandOrNameMatches)과 방향이
	 * 반대라 2등급의 부분집합이 아니다. 처음엔 "3등급은 2등급을 항상 만족한다"고 잘못 생각해
	 * 여기서 뺐었는데, 'Levis 501' 같은 결합 키워드가 통째로 걸러져 사라지는 버그로 드러났다.
	 */
	private BooleanExpression keywordMatchCondition(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}

		String trimmed = keyword.trim();
		return taggedItemExists(candidate -> brandAndNameBothMatch(candidate, trimmed)
				.or(brandOrNameMatches(candidate, trimmed))
				.or(categoryNameMatches(candidate, trimmed)));
	}

	/**
	 * 매칭 등급 (DEC-023) — 3: 브랜드+모델명 둘 다 일치 · 2: 브랜드 또는 아이템명 일치 ·
	 * 1: 카테고리명 일치 · 0: 없음. 위에서부터 먼저 맞는 조건이 등급이 된다.
	 */
	private NumberExpression<Integer> matchGrade(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			// 키워드가 없으면 필터링 없이 전부 같은 기준(0)으로 두고 rank_score 만으로 가른다
			return Expressions.asNumber(0);
		}

		String trimmed = keyword.trim();
		BooleanExpression grade3 = taggedItemExists(candidate -> brandAndNameBothMatch(candidate, trimmed));
		BooleanExpression grade2 = taggedItemExists(candidate -> brandOrNameMatches(candidate, trimmed));
		BooleanExpression grade1 = taggedItemExists(candidate -> categoryNameMatches(candidate, trimmed));

		return new CaseBuilder()
				.when(grade3).then(3)
				.when(grade2).then(2)
				.when(grade1).then(1)
				.otherwise(0);
	}

	/**
	 * 3등급은 "Levis 501"처럼 브랜드+모델명을 붙여 적은 키워드를 가리킨다(예시가 그렇다).
	 * brandName="Levis"·name="501" 를 각각 "Levis 501" 이 포함하는지 봐야 하므로, 다른
	 * 등급(필드가 키워드를 포함)과 방향이 반대다 — 키워드가 필드를 포함하는지 본다.
	 * 실제 호출로 이 방향 착오를 잡았다(처음엔 다른 등급과 같은 방향으로 짜서 'Levis 501'
	 * 검색이 아예 안 걸렸다).
	 */
	private BooleanExpression brandAndNameBothMatch(QItem candidate, String keyword) {
		return keywordContains(keyword, candidate.brandName).and(keywordContains(keyword, candidate.name));
	}

	/**
	 * keyword 가 field 값을 포함하는지 — brandAndNameBothMatch 전용. 방향이 반대라 따로 둔다.
	 *
	 * LIKE + CONCAT('%', field, '%') 로 짰다가(이전 버전) 바꿨다 — field 는 사용자가 등록한
	 * 아이템의 brandName·name 이라 그 값 자체에 '%'·'_' 가 들어가면 그 글자가 패턴의
	 * 와일드카드로 작동한다. brandName 을 '%' 한 글자로 등록하면 모든 검색에 3등급으로
	 * 걸려 노출 순위를 조작할 수 있었다. INSTR 은 패턴이 아니라 리터럴 부분일치라 이 문제가
	 * 구조적으로 없다.
	 */
	private BooleanExpression keywordContains(String keyword, StringExpression field) {
		NumberExpression<Integer> position = Expressions.numberTemplate(
				Integer.class, "function('instr', lower({0}), lower({1}))", keyword, field);
		return position.gt(0);
	}

	private BooleanExpression brandOrNameMatches(QItem candidate, String keyword) {
		return candidate.brandName.containsIgnoreCase(keyword).or(candidate.name.containsIgnoreCase(keyword));
	}

	/** 세부 카테고리명과 그 대분류명 둘 다 본다 — '상의' 로도, '반팔 티셔츠' 로도 찾을 수 있어야 한다. */
	private BooleanExpression categoryNameMatches(QItem candidate, String keyword) {
		return candidate.category.name.containsIgnoreCase(keyword)
				.or(candidate.category.parent.name.containsIgnoreCase(keyword));
	}

	/** 판매 상태 3모드. ALL 이면 조건 없음. */
	private BooleanExpression saleStatusCondition(SaleStatusFilter filter) {
		if (filter == null || filter == SaleStatusFilter.ALL) {
			return null;
		}

		return switch (filter) {
			case ONLY_ON_SALE -> taggedItemExists(candidate -> candidate.saleStatus.eq(SaleStatus.ON_SALE));
			case INCLUDE_ON_SALE -> taggedItemExists(candidate -> candidate.saleStatus.ne(SaleStatus.SOLD));
			case ALL -> null;
		};
	}

	/** 세부 카테고리 다중 선택. */
	private BooleanExpression categoryCondition(List<Long> categoryIds) {
		if (categoryIds == null || categoryIds.isEmpty()) {
			return null;
		}

		return taggedItemExists(candidate -> candidate.category.id.in(categoryIds));
	}

	private BooleanExpression authorGenderCondition(AuthorGenderFilter filter) {
		if (filter == null || filter == AuthorGenderFilter.ALL) {
			return null;
		}

		return post.author.gender.eq(Gender.valueOf(filter.name()));
	}

	private BooleanExpression heightCondition(Integer min, Integer max) {
		if (min == null && max == null) {
			return null;
		}
		BooleanExpression condition = post.author.heightCm.isNotNull();
		if (min != null) {
			condition = condition.and(post.author.heightCm.goe(min));
		}
		if (max != null) {
			condition = condition.and(post.author.heightCm.loe(max));
		}
		return condition;
	}

	private BooleanExpression weightCondition(Integer min, Integer max) {
		if (min == null && max == null) {
			return null;
		}
		BooleanExpression condition = post.author.weightKg.isNotNull();
		if (min != null) {
			condition = condition.and(post.author.weightKg.goe(min));
		}
		if (max != null) {
			condition = condition.and(post.author.weightKg.loe(max));
		}
		return condition;
	}

	/**
	 * 이 게시물에 태그된 아이템 중 조건을 만족하는 것이 있는가. 태그는 사진 단위라 한 게시물에
	 * 여러 번 걸릴 수 있어 exists 로 판정한다 — join 하면 조건을 만족하는 태그 수만큼 행이
	 * 늘어나 count·cursor 가 흔들린다 (ItemRepositoryImpl.taggedWithCandidates 와 같은 이유).
	 */
	private BooleanExpression taggedItemExists(Function<QItem, BooleanExpression> rule) {
		QItem candidate = new QItem("candidate");

		return JPAExpressions
				.selectOne()
				.from(postItemTag)
				.join(candidate).on(candidate.id.eq(postItemTag.item.id))
				.where(
						postItemTag.postImage.post.id.eq(post.id),
						candidate.deletedAt.isNull(),
						rule.apply(candidate))
				.exists();
	}

	/** LIKE·VIEW·SAVE 전용 — LATEST(createdAt)·RECOMMENDED(복합 키)는 각자 메서드가 따로 처리한다. */
	private NumberExpression<Integer> sortColumnOf(PostSortType sort) {
		return switch (sort) {
			case LIKE -> post.likeCount;
			case VIEW -> post.viewCount;
			case SAVE -> post.saveCount;
			case LATEST, RECOMMENDED -> throw new IllegalStateException("호출 쪽에서 이미 분기했어야 함: " + sort);
		};
	}

	private BooleanExpression afterCount(NumberExpression<Integer> sortColumn, Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		Integer value = cursor.asInt();
		return sortColumn.lt(value).or(sortColumn.eq(value).and(post.id.lt(cursor.getId())));
	}

	/** 게시일 커서. PostRepositoryImpl.afterCreatedAt() 과 같은 규칙(같은 시각이면 id 로 가른다). */
	private BooleanExpression afterCreatedAt(Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		LocalDateTime createdAt = cursor.asDateTime();
		return post.createdAt.lt(createdAt).or(post.createdAt.eq(createdAt).and(post.id.lt(cursor.getId())));
	}

	private BooleanExpression afterCombinedKey(NumberExpression<BigDecimal> combinedKey, Cursor cursor) {
		if (cursor == null) {
			return null;
		}
		BigDecimal value = cursor.asBigDecimal();
		return combinedKey.lt(value).or(combinedKey.eq(value).and(post.id.lt(cursor.getId())));
	}
}
