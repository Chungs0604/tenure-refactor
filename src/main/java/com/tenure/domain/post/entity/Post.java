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
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
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

}
