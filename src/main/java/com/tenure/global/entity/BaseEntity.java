package com.tenure.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 생성·수정 시각을 모두 갖는 테이블의 공통 조상.
 *
 * <p>수정 화면이 있는 테이블만 이쪽을 상속한다 — user, post, item, item_listing,
 * purchase_offer, trade_order, payment, shipping_address, payout_account, post_item_tag.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseCreatedEntity {

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
}
