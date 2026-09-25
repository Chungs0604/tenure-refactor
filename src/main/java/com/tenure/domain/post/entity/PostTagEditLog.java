package com.tenure.domain.post.entity;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 태그 수정 이력. MVP 외.
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "post_tag_edit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class PostTagEditLog {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 수정 대상 태그 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_item_tag_id", nullable = false)
	private PostItemTag postItemTag;

	/** 변경 전 아이템 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "before_item_id")
	private Item beforeItem;

	/** 변경 후 아이템 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "after_item_id")
	private Item afterItem;

	/** 수정자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "edited_by", nullable = false)
	private User editor;

	/** 수정 시각 */
	@Column(name = "edited_at", nullable = false)
	private LocalDateTime editedAt;

}
