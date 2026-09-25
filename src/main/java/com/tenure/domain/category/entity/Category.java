package com.tenure.domain.category.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 2단 카테고리(카테고리 > 세부 카테고리). 자기참조.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Category {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** depth=1 이면 NULL */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Category parent;

	/** 대분류 10종(화면 순서): 아우터·상의·하의·신발·가방·모자·치마·원피스·액세서리·주얼리. 세부 102종. 값은 V2__seed_category.sql 이 정본 */
	@Column(name = "name", nullable = false, length = 50)
	private String name;

	/** 1=카테고리, 2=세부 카테고리 */
	@Column(name = "depth", nullable = false)
	private Byte depth;

	/** '아이템 상세 정보 수정 (카테고리마다 세부 카테고리, 사이즈 다름)' — 카테고리별 사이즈 체계가 다르다(→ Q6) */
	@Column(name = "size_scheme", length = 30)
	private String sizeScheme;

	/** 드롭다운 노출 순서 */
	@Column(name = "sort_order", nullable = false)
	private Short sortOrder;

}
