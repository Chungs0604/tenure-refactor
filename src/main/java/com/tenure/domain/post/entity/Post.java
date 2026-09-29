package com.tenure.domain.post.entity;

import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * OOTD 게시물. 착용 날짜를 갖고 마이페이지에서 날짜별로 조회된다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Post extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 게시물 작성자. OOTD 상세 상단 프로필 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private User author;

	/** '달력 아이콘 클릭' → '날짜 선택'. 마이페이지 날짜별 캘린더와 '최근 착용'의 기준 */
	@Column(name = "worn_date", nullable = false)
	private LocalDate wornDate;

	/** 촬영 비율 3종 */
	@Enumerated(EnumType.STRING)
	@Column(name = "aspect_ratio", nullable = false, length = 15)
	private AspectRatio aspectRatio;

	/** '자동 태그되어 바로 게시됨'. 태그 일괄 수정 대상 판별(3일 이내)에 필요 */
	@Column(name = "quick_posted", nullable = false)
	private Boolean quickPosted;

	/** '게시물 삭제하기'. soft delete */
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	/** OOTD 상세 하단 '♡ 1234'. 정본은 post_like 테이블 */
	@Column(name = "like_count", nullable = false)
	private Integer likeCount;

	/** OOTD 상세 하단 '🔖 1234'. 정본은 post_save 테이블 */
	@Column(name = "save_count", nullable = false)
	private Integer saveCount;

	/** 검색 결과의 '조회수 순' 정렬. Redis 카운터를 주기적으로 반영한다 (DEC-019) */
	@Column(name = "view_count", nullable = false)
	private Integer viewCount;

	/**
	 * 검색 '추천 순'의 정렬 키. 인기와 신선도를 합친 값이며 배치가 갱신한다 (DEC-023).
	 *
	 * 조회할 때마다 계산하지 않는다 — 함수 결과로 정렬하면 인덱스를 못 타고, 요청 사이에 값이
	 * 바뀌면 커서 경계가 흔들려 스크롤에서 중복·누락이 난다.
	 */
	@Column(name = "rank_score", nullable = false, precision = 6, scale = 4)
	private BigDecimal rankScore;


	/**
	 * 게시. 카운터는 0 에서 시작한다.
	 *
	 * 사진과 태그는 함께 받지 않는다 — 사진이 post_id 를 알아야 하므로 게시물이 먼저
	 * 저장돼야 하고, 태그 자격 검사(본인 보유 아이템)는 Service 가 아이템을 조회해야 판정할 수 있다.
	 */
	public static Post publish(
			User author, LocalDate wornDate, AspectRatio aspectRatio, boolean quickPosted
	) {
		return Post.builder()
				.author(author)
				.wornDate(wornDate)
				.aspectRatio(aspectRatio)
				.quickPosted(quickPosted)
				.likeCount(0)
				.saveCount(0)
				.viewCount(0)
				// 갓 올라온 게시물의 점수는 배치가 처음 돌 때 채운다
				.rankScore(BigDecimal.ZERO)
				.build();
	}

	/** 삭제. 복구 화면이 없을 뿐 행은 남는다 — 대표 OOTD·신고·착용 기록이 이 행을 참조한다. */
	public void softDelete() {
		this.deletedAt = LocalDateTime.now();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	/** LAZY 프록시의 id 는 DB 를 치지 않고 읽는다. */
	public boolean isAuthor(Long userId) {
		return author.getId().equals(userId);
	}

	public void increaseLikeCount() {
		this.likeCount++;
	}

	/**
	 * 좋아요 취소. 관계가 있을 때만 호출되지만 0 아래로는 내리지 않는다 —
	 * 동시 요청으로 한 번 어긋나면 화면에 음수가 그대로 뜬다.
	 */
	public void decreaseLikeCount() {
		this.likeCount = Math.max(0, this.likeCount - 1);
	}

	public void increaseSaveCount() {
		this.saveCount++;
	}

	public void decreaseSaveCount() {
		this.saveCount = Math.max(0, this.saveCount - 1);
	}

}
