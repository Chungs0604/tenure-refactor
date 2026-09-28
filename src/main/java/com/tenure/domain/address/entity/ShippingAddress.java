package com.tenure.domain.address.entity;

import com.tenure.domain.address.enums.DeliveryRequestType;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	/** '우편번호' 07027. 주소 검색이 도로명주소와 함께 돌려준다 */
	@Column(name = "zipcode", nullable = false, length = 10)
	private String zipcode;

	/** '주소' 서울특별시 동작구 상도로 369. '계정 주소 검색' 프레임 존재 */
	@Column(name = "address", nullable = false, length = 255)
	private String address;

	/** '상세 주소' 104동 201호 */
	@Column(name = "address_detail", length = 255)
	private String addressDetail;

	/** '배송 요청사항(선택)' 드롭다운 4종. 고르지 않으면 NULL */
	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_request_type", length = 30)
	private DeliveryRequestType deliveryRequestType;

	/** 드롭다운에서 '직접 입력'을 고르면 열리는 텍스트 필드. DIRECT 일 때만 채운다 */
	@Column(name = "delivery_request", length = 50)
	private String deliveryRequest;

	/** '기본 배송지로 설정', '클릭으로 기본 배송지 변경' */
	@Column(name = "is_default", nullable = false)
	private Boolean isDefault;

	/** 배송지 등록. 첫 배송지는 기본으로 지정된다. */
	public static ShippingAddress register(User user, String recipientName, String phone,
			String zipcode, String address, String addressDetail,
			DeliveryRequestType deliveryRequestType, String deliveryRequest,
			boolean isDefault
	) {
		return ShippingAddress.builder()
				.user(user)
				.recipientName(recipientName)
				.phone(phone)
				.zipcode(zipcode)
				.address(address)
				.addressDetail(addressDetail)
				.deliveryRequestType(deliveryRequestType)
				.deliveryRequest(normalizeRequest(deliveryRequestType, deliveryRequest))
				.isDefault(isDefault)
				.build();
	}

	/**
	 * 전체 수정. 이력을 남기지 않고 값을 덮어쓴다.
	 *
	 * 과거 거래는 order_shipment 가 그 시점 값을 복사해 갖고 있어 영향을 받지 않는다 (DEC-013).
	 * 기본 배송지 여부는 건드리지 않는다 — 별도 동작이다.
	 */
	public void update(String recipientName, String phone,
			String zipcode, String address, String addressDetail,
			DeliveryRequestType deliveryRequestType, String deliveryRequest
	) {
		this.recipientName = recipientName;
		this.phone = phone;
		this.zipcode = zipcode;
		this.address = address;
		this.addressDetail = addressDetail;
		this.deliveryRequestType = deliveryRequestType;
		this.deliveryRequest = normalizeRequest(deliveryRequestType, deliveryRequest);
	}

	/**
	 * 고정 문구를 고른 요청사항은 직접 입력값을 버린다.
	 *
	 * 남겨두면 유형은 '문 앞에 놓아주세요'인데 본문은 이전에 쓴 문장인 행이 생긴다 —
	 * 주문 시 order_shipment 로 그대로 복사되므로 배송 기사에게 잘못된 문구가 간다.
	 */
	private static String normalizeRequest(DeliveryRequestType type, String deliveryRequest) {
		return type != null && type.isDirect() ? deliveryRequest : null;
	}

	/** 기본 배송지로 올리거나 내린다. 사용자당 1건이라는 단일성은 Service 가 지킨다. */
	public void changeDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public boolean isDefaultAddress() {
		return Boolean.TRUE.equals(isDefault);
	}
}
