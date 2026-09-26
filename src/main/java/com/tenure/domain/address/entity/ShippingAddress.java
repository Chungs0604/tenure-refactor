package com.tenure.domain.address.entity;

import com.tenure.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송지 주소록. 주문 시에는 OrderShipment 로 값을 스냅샷한다.
 *
 * 무엇이 필수 인자인지는 도메인 규칙이라 스키마에서 읽을 수 없다.
 * {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "shipping_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class ShippingAddress extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 소유자 FK */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** '이름' / '받는 분' / '수령인' */
	@Column(name = "recipient_name", nullable = false, length = 50)
	private String recipientName;

	/** '휴대폰 번호' / '연락처' 010-1234-5678 */
	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	/** '주소' 서울특별시 동작구 상도로 369. '계정 주소 검색' 프레임 존재 */
	@Column(name = "address", nullable = false, length = 255)
	private String address;

	/** '상세 주소' 104동 201호 */
	@Column(name = "address_detail", length = 255)
	private String addressDetail;

	/** '배송 요청사항(선택)' */
	@Column(name = "delivery_request", length = 255)
	private String deliveryRequest;

	/** '기본 배송지로 설정', '클릭으로 기본 배송지 변경' */
	@Column(name = "is_default", nullable = false)
	private Boolean isDefault;

	/** 배송지 등록. 첫 배송지는 기본으로 지정된다 (API-021). */
	public static ShippingAddress register(User user, String recipientName, String phone,
			String address, String addressDetail, String deliveryRequest, boolean isDefault
	) {
		return ShippingAddress.builder()
				.user(user)
				.recipientName(recipientName)
				.phone(phone)
				.address(address)
				.addressDetail(addressDetail)
				.deliveryRequest(deliveryRequest)
				.isDefault(isDefault)
				.build();
	}
}
