package com.tenure.domain.category.entity;

import com.tenure.domain.category.enums.SizeScheme;
import com.tenure.domain.category.enums.SizeSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리별 사이즈 칩. 값은 V15__size_option.sql 이 정본이며 애플리케이션이 만들지 않는다.
 *
 * 프론트가 칩을 하드코딩하지 않도록 API-090 이 카테고리 트리와 함께 내려준다.
 * item.size_label 은 이 표의 label 을 복사해 넣는다 — FK 로 묶으면 체계를 손볼 때
 * 이미 등록된 아이템의 기록이 흔들린다.
 */
@Entity
@Table(name = "size_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SizeOption {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 칩 세트. category.size_scheme 이 가리킨다 */
	@Enumerated(EnumType.STRING)
	@Column(name = "scheme", nullable = false, length = 20)
	private SizeScheme scheme;

	/** 그 칩이 속한 체계. 칩을 줄로 묶는 근거 */
	@Enumerated(EnumType.STRING)
	@Column(name = "size_system", nullable = false, length = 20)
	private SizeSystem sizeSystem;

	/** 화면에 그대로 쓰는 칩 라벨. item.size_label 에 이 값이 들어간다 */
	@Column(name = "label", nullable = false, length = 20)
	private String label;

	/** 칩 노출 순서 */
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

}
