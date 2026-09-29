package com.tenure.domain.listing.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.listing.enums.FeeBearer;
import com.tenure.domain.listing.enums.SaleStatus;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이템의 판매 등록('판매 전환'). 아이템이 재판매될 때마다 새 행이 생기므로 Item 과 1:N 이다. 생성 전제로 판매자에게 PayoutAccount 가 있어야 한다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "item_listing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ItemListing extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 판매 대상 아이템 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** '판매자 정보' 섹션 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "seller_id", nullable = false)
	private User seller;

	/** '판매 중' / '미판매' / 판매 완료 배지 */
	@Enumerated(EnumType.STRING)
	@Column(name = "sale_status", nullable = false, length = 20)
	private SaleStatus saleStatus;

	/** '구매 제안 O' / '구매 제안 X', '구매 제안 허용' 토글 */
	@Column(name = "offer_allowed", nullable = false)
	private Boolean offerAllowed;

	/** '가격' 360,000원. 미판매면 NULL */
	@Column(name = "price")
	private Integer price;

	/** '배송비' +5,000원. 기본 사용자는 고정값 */
	@Column(name = "shipping_fee", nullable = false)
	private Integer shippingFee;

	/** '수수료 부담'. 기본 사용자는 고정값 */
	@Enumerated(EnumType.STRING)
	@Column(name = "fee_bearer", nullable = false, length = 10)
	private FeeBearer feeBearer;

	/** '판매자 설명' */
	@Column(name = "seller_note", columnDefinition = "TEXT")
	private String sellerNote;

	/** '관심 14명' — 화면에 수치가 표시된다. 비정규화 카운터이며 정본은 wishlist 테이블(→ 파생 컬럼 대장) */
	@Column(name = "wish_count", nullable = false)
	private Integer wishCount;

	/** '판매 게시가 완료됐습니다.' */
	@Column(name = "listed_at")
	private LocalDateTime listedAt;

	/** '미판매로 변경시 화면' — 판매 내림 시각 */
	@Column(name = "closed_at")
	private LocalDateTime closedAt;

}
