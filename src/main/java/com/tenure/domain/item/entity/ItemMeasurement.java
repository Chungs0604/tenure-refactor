package com.tenure.domain.item.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매 상품 실측. 10개 고정 필드이며 카테고리별 차이는 '어떤 걸 채우느냐'로만 표현된다. JPA 에서는 값 객체(@Embeddable)로 매핑한다.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "item_measurement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ItemMeasurement {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 아이템당 1행 (1:1) */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** 어깨 너비(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "shoulder_width", precision = 5, scale = 1)
	private BigDecimal shoulderWidth;

	/** 가슴 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "chest_width", precision = 5, scale = 1)
	private BigDecimal chestWidth;

	/** 소매길이(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "sleeve_length", precision = 5, scale = 1)
	private BigDecimal sleeveLength;

	/** 총 기장(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "total_length", precision = 5, scale = 1)
	private BigDecimal totalLength;

	/** 허리 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "waist_width", precision = 5, scale = 1)
	private BigDecimal waistWidth;

	/** 허벅지 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "thigh_width", precision = 5, scale = 1)
	private BigDecimal thighWidth;

	/** 밑위(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "rise", precision = 5, scale = 1)
	private BigDecimal rise;

	/** 인심(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "inseam", precision = 5, scale = 1)
	private BigDecimal inseam;

	/** 밑단(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "hem_width", precision = 5, scale = 1)
	private BigDecimal hemWidth;

	/** 엉덩이 단면(cm). 카테고리별로 필수·금지가 갈린다 (사용자 지정 사양) */
	@Column(name = "hip_width", precision = 5, scale = 1)
	private BigDecimal hipWidth;

}
