package com.tenure.domain.follow.entity;

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
 * 팔로우 관계. 마이페이지 팔로워/팔로잉 수의 원천.
 */
@Entity
@Table(name = "follow")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Follow extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 팔로우를 건 사용자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "follower_id", nullable = false)
	private User follower;

	/** 팔로우 대상 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "following_id", nullable = false)
	private User following;

	/** 팔로우. 자기 자신 여부·중복 여부는 Service 가 가린다. */
	public static Follow create(User follower, User following) {
		return Follow.builder()
				.follower(follower)
				.following(following)
				.build();
	}

}
