package com.tenure.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 생성 시각만 갖는 테이블의 공통 조상.
 *
 * <p>스키마 정본이 "모든 테이블에 created_at, <b>변경 가능한</b> 테이블에 updated_at" 으로
 * 규정한다. 한 번 쌓이면 바뀌지 않는 append-only·조인 테이블(follow, post_like,
 * item_ownership, item_wear_record …)은 updated_at 을 두지 않으므로 이쪽을 상속한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
