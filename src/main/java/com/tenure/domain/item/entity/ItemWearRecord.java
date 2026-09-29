package com.tenure.domain.item.entity;

import com.tenure.domain.item.enums.WearRecordVoidReason;
import com.tenure.domain.post.entity.Post;
import com.tenure.global.entity.BaseCreatedEntity;
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
 * 착용 기록('OOTD 인증'). PostItemTag 에서 파생 가능하지만 별도 테이블로 둔다 — 인증 횟수가 소유 구간별로 집계되므로 ownership_id 가 필요하고, 아이템이 승계된 뒤에도 이전 소유자의 인증 이력이 그대로 남아야 한다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "item_wear_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ItemWearRecord extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 대상 아이템 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** 소유 구간별 'OOTD 인증 N회' 집계 단위 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ownership_id", nullable = false)
	private ItemOwnership ownership;

	/** 인증 근거 게시물. '아이템 히스토리'가 이 링크로 사진을 모은다 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;

	/** '최근 착용' 계산 기준. post.worn_date 를 복사 */
	@Column(name = "worn_date", nullable = false)
	private LocalDate wornDate;

	/** 태그가 다른 아이템으로 바뀌거나 삭제되면 이 인증을 무효화한다. 행은 지우지 않는다(append-only). 인증 횟수 집계는 WHERE voided_at IS NULL */
	@Column(name = "voided_at")
	private LocalDateTime voidedAt;

	/** 무효화 사유. voided_at 이 NULL 이 아닐 때만 채워진다 */
	@Enumerated(EnumType.STRING)
	@Column(name = "voided_reason", length = 30)
	private WearRecordVoidReason voidedReason;


	/**
	 * OOTD 인증 적립. 태그가 붙는 순간 만든다.
	 *
	 * wornDate 는 post.worn_date 의 스냅샷이다 (DEC-013) — 게시물의 착용일이
	 * 나중에 바뀌어도 이미 적립된 인증의 날짜는 그대로다.
	 */
	public static ItemWearRecord certify(
			Item item, ItemOwnership ownership, Post post, LocalDate wornDate
	) {
		return ItemWearRecord.builder()
				.item(item)
				.ownership(ownership)
				.post(post)
				.wornDate(wornDate)
				.build();
	}

	/**
	 * 인증 무효화. 행은 지우지 않는다 (append-only).
	 *
	 * 집계는 voided_at IS NULL 로 세므로 무효화한 인증은 'OOTD 인증 N회' 에서 빠진다.
	 */
	public void invalidate(WearRecordVoidReason reason) {
		this.voidedAt = LocalDateTime.now();
		this.voidedReason = reason;
	}

	/**
	 * 무효화 취소.
	 *
	 * uk_item_wear_record(item_id, post_id) 때문에 같은 조합을 다시 태그할 때
	 * INSERT 하면 중복 키로 실패한다. 새 행이 아니라 이 행을 되살린다.
	 */
	public void restore() {
		this.voidedAt = null;
		this.voidedReason = null;
	}

	public boolean isVoided() {
		return voidedAt != null;
	}

}
