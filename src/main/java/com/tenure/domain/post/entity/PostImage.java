package com.tenure.domain.post.entity;

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
 * 게시물 사진(다중). 대표 사진 플래그를 갖는다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "post_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PostImage {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 부모 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;

	/** 촬영·앨범 이미지. 저장소 키를 넣는다 (DEC-006) */
	@Column(name = "image_key", nullable = false, length = 500)
	private String imageKey;

	/** '바로 게시한 사진들이 순서대로 보여요' */
	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	/** '대표 사진' / '대표 OOTD' */
	@Column(name = "is_cover", nullable = false)
	private Boolean isCover;


	/** 사진 한 장. 대표 여부는 Service 가 정해서 넘긴다 — 게시물당 1장이라는 규칙이 밖에 있다. */
	public static PostImage of(Post post, String imageKey, int sortOrder, boolean isCover) {
		return PostImage.builder()
				.post(post)
				.imageKey(imageKey)
				.sortOrder(sortOrder)
				.isCover(isCover)
				.build();
	}

}
