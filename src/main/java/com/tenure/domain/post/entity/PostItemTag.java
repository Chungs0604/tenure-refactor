package com.tenure.domain.post.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.global.entity.BaseEntity;
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
 * 사진 속 아이템 태그. 게시물과 아이템을 잇는 서비스의 중심 연결고리.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "post_item_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PostItemTag extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 부모 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_image_id", nullable = false)
	private PostImage postImage;

	/** 'pill text / Our Legacy 니트' 태그가 아이템을 가리킨다 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** '원하는 아이템 클릭하면 해당 아이템에 사각형이 쳐진다' — 좌표 필요. 이미지 폭 대비 비율로 저장 */
	@Column(name = "pos_x", nullable = false, precision = 5, scale = 4)
	private BigDecimal posX;

	/** 이미지 높이 대비 비율 */
	@Column(name = "pos_y", nullable = false, precision = 5, scale = 4)
	private BigDecimal posY;

}
