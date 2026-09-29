package com.tenure.domain.item.entity;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.enums.WearTarget;
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
 * 패션 아이템. 소유자가 바뀌어도 이 행은 유지된다 — 착용 기록이 아이템에 영구 귀속되어야 "구매자는 아이템의 기록을 이어서 받게됩니다"가 성립한다. 현재 소유자와 보유 기간은 ItemOwnership 에서 조회한다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Item extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 최초 등록자. 'Tenure 기록 기준' 최초 구간의 '최초 등록' 뱃지 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registrant_id", nullable = false)
	private User registrant;

	/** '아이템명'. 예 'LVC 1955 501' */
	@Column(name = "name", nullable = false, length = 100)
	private String name;

	/** '브랜드명'. 예 'Levis' */
	@Column(name = "brand_name", nullable = false, length = 100)
	private String brandName;

	/** '세부 카테고리'(depth=2)를 가리킨다 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	/** '사이즈'. 예 'L' */
	@Column(name = "size_label", length = 20)
	private String sizeLabel;

	/** '착용 대상'. 예 '남성복' */
	@Enumerated(EnumType.STRING)
	@Column(name = "wear_target", nullable = false, length = 10)
	private WearTarget wearTarget;

	/** '대표 사진' (필수 표시 *). 저장소 키를 넣는다 (DEC-006) */
	@Column(name = "cover_image_key", nullable = false, length = 500)
	private String coverImageKey;

	/** '제품 설명' / '상품 설명' */
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	/** '최초 보유 날짜'. 예 25.02.8. 최초 item_ownership 구간의 start_date 와 같은 값이며, 거래 전까지만 수정할 수 있다 */
	@Column(name = "first_owned_date")
	private LocalDate firstOwnedDate;

	/** '삭제된 아이템' 상태. soft delete */
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;


	/**
	 * 아이템 등록.
	 *
	 * 소유 구간(ItemOwnership)은 함께 만들지 않는다 — 아이템이 먼저 저장돼야 구간이
	 * item_id 를 가질 수 있다. 등록 서비스가 두 행을 한 트랜잭션에서 만든다.
	 */
	public static Item register(
			User registrant, Category category, String name, String brandName,
			String sizeLabel, WearTarget wearTarget, String coverImageKey,
			String description, LocalDate firstOwnedDate
	) {
		return Item.builder()
				.registrant(registrant)
				.category(category)
				.name(name)
				.brandName(brandName)
				.sizeLabel(sizeLabel)
				.wearTarget(wearTarget)
				.coverImageKey(coverImageKey)
				.description(description)
				.firstOwnedDate(firstOwnedDate)
				.build();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

}
