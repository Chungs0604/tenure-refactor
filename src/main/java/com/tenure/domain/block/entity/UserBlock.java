package com.tenure.domain.block.entity;

import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseCreatedEntity;
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
 * 사용자 차단. 해제가 있으므로 행 삭제로 처리한다.
 */
@Entity
@Table(name = "user_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class UserBlock extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 차단한 사용자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "blocker_id", nullable = false)
	private User blocker;

	/** '이 사용자를 차단할까요?' */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "blocked_id", nullable = false)
	private User blocked;

	/** 차단. 자기 자신 여부·중복 여부는 Service 가 가린다. */
	public static UserBlock create(User blocker, User blocked) {
		return UserBlock.builder()
				.blocker(blocker)
				.blocked(blocked)
				.build();
	}

}
