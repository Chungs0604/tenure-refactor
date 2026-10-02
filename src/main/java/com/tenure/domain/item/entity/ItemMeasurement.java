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
import com.tenure.domain.category.enums.MeasurementField;
import java.util.EnumMap;
import java.util.Map;

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



	/**
	 * 판매 전환이 받은 실측. 세트에 없는 항목은 맵에 아예 없고 컬럼에는 null 로 남는다.
	 *
	 * 위치 인자 10개 대신 키로 받는다 — 같은 타입이 줄줄이 서면 둘을 바꿔 넣어도 컴파일이
	 * 통과한다. 기록으로 가치를 증명하는 서비스라 원장이 조용히 틀어지는 경로를 막는다.
	 *
	 * 맵이 세트와 맞는지는 Service 가 본다 — 카테고리를 읽어야 알 수 있다.
	 */
	public static ItemMeasurement of(Item item, Map<MeasurementField, BigDecimal> values) {
		return ItemMeasurement.builder()
				.item(item)
				.shoulderWidth(values.get(MeasurementField.SHOULDER_WIDTH))
				.chestWidth(values.get(MeasurementField.CHEST_WIDTH))
				.sleeveLength(values.get(MeasurementField.SLEEVE_LENGTH))
				.totalLength(values.get(MeasurementField.TOTAL_LENGTH))
				.waistWidth(values.get(MeasurementField.WAIST_WIDTH))
				.thighWidth(values.get(MeasurementField.THIGH_WIDTH))
				.rise(values.get(MeasurementField.RISE))
				.inseam(values.get(MeasurementField.INSEAM))
				.hemWidth(values.get(MeasurementField.HEM_WIDTH))
				.hipWidth(values.get(MeasurementField.HIP_WIDTH))
				.build();
	}

	/**
	 * 채워진 항목만 담아 돌려준다. 비어 있는 컬럼은 키가 없다 —
	 * 응답에 null 이 끼지 않아 화면이 그릴 칸만 받는다.
	 */
	public Map<MeasurementField, BigDecimal> toValues() {
		Map<MeasurementField, BigDecimal> values = new EnumMap<>(MeasurementField.class);
		putIfPresent(values, MeasurementField.SHOULDER_WIDTH, shoulderWidth);
		putIfPresent(values, MeasurementField.CHEST_WIDTH, chestWidth);
		putIfPresent(values, MeasurementField.SLEEVE_LENGTH, sleeveLength);
		putIfPresent(values, MeasurementField.TOTAL_LENGTH, totalLength);
		putIfPresent(values, MeasurementField.WAIST_WIDTH, waistWidth);
		putIfPresent(values, MeasurementField.THIGH_WIDTH, thighWidth);
		putIfPresent(values, MeasurementField.RISE, rise);
		putIfPresent(values, MeasurementField.INSEAM, inseam);
		putIfPresent(values, MeasurementField.HEM_WIDTH, hemWidth);
		putIfPresent(values, MeasurementField.HIP_WIDTH, hipWidth);
		return values;
	}

	private static void putIfPresent(
			Map<MeasurementField, BigDecimal> values, MeasurementField field, BigDecimal value
	) {
		if (value != null) {
			values.put(field, value);
		}
	}

}
