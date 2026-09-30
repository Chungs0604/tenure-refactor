package com.tenure.domain.item.entity;

import com.tenure.domain.category.entity.Category;
import com.tenure.domain.item.enums.SaleStatus;
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

	/**
	 * '제품 설명' / '상품 설명'.
	 *
	 * 입력 화면도 표시 화면도 없어 API 로는 채우지 않는다 — 화면의 '상품 설명' 은
	 * 판매 전환에서 받는 item_listing.seller_note 다. 컬럼은 남겨 두고 쓰는 화면이 생기면 연다.
	 */
	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	/**
	 * '최초 보유 날짜'. 예 25.02.8. 최초 item_ownership 구간의 start_date 와 같은 값이다.
	 *
	 * 등록 시점에 정해지고 그 뒤로는 바꿀 수 없다 (DEC-007) — 구매자가 보고 산 'Tenure 기록 기준' 의
	 * 첫 줄이라 사후에 달라지면 안 된다. 그래서 변경 메서드가 없다.
	 */
	@Column(name = "first_owned_date")
	private LocalDate firstOwnedDate;

	/**
	 * 히어로 하단 배지 '판매 중' / '미판매' / '판매 완료' (DEC-026).
	 *
	 * 판매 등록이 없는 아이템도 '미판매' 와 '판매 완료' 를 표시하므로 아이템이 갖는다 —
	 * 미판매 공개 페이지(2825:58233)에는 item_listing 행이 아예 없고,
	 * 미판매 아이템이 구매 제안으로 팔린 뒤의 화면(3465:33567)도 마찬가지다.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "sale_status", nullable = false, length = 20)
	private SaleStatus saleStatus;

	/**
	 * '구매 제안 O' / '구매 제안 X'. 판매 등록과 무관하게 아이템에 붙는다 (DEC-024).
	 *
	 * 미판매 아이템에는 item_listing 행이 없는데 미판매 공개 페이지가 이 토글을 그린다.
	 */
	@Column(name = "offer_allowed", nullable = false)
	private Boolean offerAllowed;

	/**
	 * '관심 N명'. 정본은 wishlist 이고 이 컬럼은 비정규화 카운터다 (DEC-024).
	 *
	 * 판매를 내렸다 다시 걸면 item_listing 행이 새로 생기므로 거기 두면 수가 리셋된다.
	 */
	@Column(name = "wish_count", nullable = false)
	private Integer wishCount;

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
			LocalDate firstOwnedDate
	) {
		return Item.builder()
				.registrant(registrant)
				.category(category)
				.name(name)
				.brandName(brandName)
				.sizeLabel(sizeLabel)
				.wearTarget(wearTarget)
				.coverImageKey(coverImageKey)
				.firstOwnedDate(firstOwnedDate)
				.saleStatus(SaleStatus.NOT_FOR_SALE)
				.offerAllowed(false)
				.wishCount(0)
				.build();
	}

	/**
	 * 상세 수정. 폼 전체를 교체한다 — 보낸 것만 바꾸는 부분 수정이 아니다.
	 *
	 * 상세 수정 바텀시트(2825:49332 외 9종)는 현재 값이 전부 채워진 채 열리고 CTA 가
	 * '초기화' / '적용하기' 둘뿐이라, 클라이언트가 일부만 보내는 경우가 없다. null 을
	 * '안 보냄'으로 해석해 기존 값을 살려 두면 빠뜨린 필드가 조용히 통과한다 — 요청 DTO 가
	 * 필수로 막고 여기서는 받은 값을 그대로 쓴다.
	 *
	 * 대표 사진은 받지 않는다. 이 시트에 입력란이 없고 판매 전환이 담당한다.
	 * 최초 보유 날짜도 받지 않는다 — 등록 뒤 불변이다 (DEC-007).
	 */
	public void update(
			Category category, String name, String brandName,
			String sizeLabel, WearTarget wearTarget
	) {
		this.category = category;
		this.name = name;
		this.brandName = brandName;
		this.sizeLabel = sizeLabel;
		this.wearTarget = wearTarget;
	}

	/** '구매 제안 허용' 토글. 미판매 아이템에도 걸린다 (DEC-024). */
	public void changeOfferAllowed(boolean offerAllowed) {
		this.offerAllowed = offerAllowed;
	}

	/**
	 * 판매 상태 전이 (DEC-026).
	 *
	 * 네 지점에서 부른다 — 판매 전환(ON_SALE) · 미판매로 변경(NOT_FOR_SALE) ·
	 * 결제 완료(SOLD) · 거래 취소(ON_SALE 복구). 판매 완료로 변경 메뉴는 수동 SOLD 다.
	 * 전이 가능 여부는 판매 등록·거래를 함께 봐야 알 수 있어 Service 가 판정한다.
	 */
	public void changeSaleStatus(SaleStatus saleStatus) {
		this.saleStatus = saleStatus;
	}

	public boolean isOnSale() {
		return saleStatus == SaleStatus.ON_SALE;
	}

	/** soft delete. 태그는 남아 '삭제된 아이템' 으로 표시된다. */
	public void delete(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

}
