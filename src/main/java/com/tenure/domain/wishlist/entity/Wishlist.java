package com.tenure.domain.wishlist.entity;

import com.tenure.domain.item.entity.Item;
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
 * 관심 등록(위시리스트). Item 기준이다 — 목록에 '판매중'/'미판매' 배지가 함께 뜨므로 판매 등록 전 아이템도 담을 수 있어야 한다.
 */
@Entity
@Table(name = "wishlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Wishlist extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 관심 등록한 사용자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 관심 아이템 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private Item item;

	/** '위시리스트 알림 활성화' / '알림 비활성화' 프레임 2종, 항목별 벨 아이콘 */
	@Column(name = "alarm_enabled", nullable = false)
	private Boolean alarmEnabled;

	/**
	 * 관심 등록. 아이템 상세의 벨 하나가 등록·알림을 동시에 켜므로 알림은 항상 켠 채로 시작함
	 * — 담기만 하고 알림을 끈 상태로 만드는 입구는 없음.
	 */
	public static Wishlist register(User user, Item item) {
		return Wishlist.builder()
				.user(user)
				.item(item)
				.alarmEnabled(true)
				.build();
	}

	/** 위시리스트 알림 on/off. */
	public void changeAlarmEnabled(boolean alarmEnabled) {
		this.alarmEnabled = alarmEnabled;
	}

}
