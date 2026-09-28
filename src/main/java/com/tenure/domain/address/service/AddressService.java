package com.tenure.domain.address.service;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.request.ShippingAddressUpdateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.exception.AddressErrorCode;
import com.tenure.domain.address.mapper.AddressMapper;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송지 주소록.
 *
 * 이력을 쌓지 않는다 — 거래는 제안 전송 시점에 배송지 값을 order_shipment 로 복사하므로
 * 주소록을 고치거나 지워도 과거 거래는 그대로다
 *
 * 기본 배송지는 사용자당 1건이다. 해제 → 지정 순서는 여기서 지키고,
 * 동시 요청까지는 uk_shipping_address_default 가 막는다.
 *
 * 예외를 던지기 전에 판단 근거를 남긴다 — ErrorCode 자체는 ServiceLoggingAspect 가 찍는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

	private final ShippingAddressRepository shippingAddressRepository;
	private final UserRepository userRepository;
	private final AddressMapper addressMapper;

	/** 배송지 목록. 개수가 적어 페이징하지 않는다. */
	@Loggable("배송지 목록 조회")
	public List<ShippingAddressResponse> getAddresses(Long userId) {

		List<ShippingAddress> allOfShippingAddress = shippingAddressRepository.findAllOf(userId);
		return addressMapper.toShippingAddressResponses(allOfShippingAddress);
	}

	/** 배송지 추가. 기본으로 넣으면 기존 기본이 내려간다. */
	@Transactional
	@Loggable("배송지 추가")
	public ShippingAddressIdResponse create(Long userId, ShippingAddressCreateRequest request) {
		User user = findUser(userId);

		boolean asDefault = Boolean.TRUE.equals(request.getIsDefault());

		// 기본으로 넣는다면 기존 기본을 먼저 내린다. 가입이 기본 배송지를 만들고 삭제가 기본을
		// 거부하므로 기본은 항상 1건 있다 — '첫 배송지면 승격' 판정이 필요 없다
		if (asDefault) {
			shippingAddressRepository.releaseDefault(userId);
		}

		ShippingAddress address = ShippingAddress.register(
				user,
				request.getRecipientName(),
				request.getPhone(),
				request.getZipcode(),
				request.getAddress(),
				request.getAddressDetail(),
				request.getDeliveryRequestType(),
				request.getDeliveryRequest(),
				asDefault
		);

		ShippingAddress saveAddress = shippingAddressRepository.save(address);

		return ShippingAddressIdResponse.builder()
				.id(saveAddress.getId())
				.build();
	}

	/** 배송지 수정. 보낸 값으로 전체를 교체한다. */
	@Transactional
	@Loggable("배송지 수정")
	public ShippingAddressIdResponse update(
			Long userId, Long addressId, ShippingAddressUpdateRequest request
	) {
		ShippingAddress address = findOwned(userId, addressId);

		address.update(
				request.getRecipientName(),
				request.getPhone(),
				request.getZipcode(),
				request.getAddress(),
				request.getAddressDetail(),
				request.getDeliveryRequestType(),
				request.getDeliveryRequest()
		);

		return ShippingAddressIdResponse.builder()
				.id(address.getId())
				.build();
	}

	/**
	 * 배송지 삭제. 하드 삭제다.
	 *
	 * 기본 배송지는 지울 수 없다 — 다른 배송지를 기본으로 지정한 뒤에 지운다.
	 * 승격 대상을 시스템이 고르면 어느 것이 기본이 됐는지 사용자가 알 수 없다.
	 */
	@Transactional
	@Loggable("배송지 삭제")
	public void delete(Long userId, Long addressId) {
		ShippingAddress address = findOwned(userId, addressId);

		if (address.isDefaultAddress()) {
			log.warn("기본 배송지를 삭제하려 했습니다. addressId={}", addressId);
			throw new CustomException(AddressErrorCode.ADDRESS_409_LAST_DEFAULT);
		}

		shippingAddressRepository.delete(address);
	}

	/** 기본 배송지 지정. 기존 기본을 내리고 이 배송지를 올린다. */
	@Transactional
	@Loggable("기본 배송지 지정")
	public void changeDefault(Long userId, Long addressId) {
		ShippingAddress address = findOwned(userId, addressId);

		// 이미 기본이면 내렸다 올리는 셈이라 아무것도 하지 않는다
		if (address.isDefaultAddress()) {
			return;
		}

		shippingAddressRepository.releaseDefault(userId);
		address.changeDefault(true);
	}

	/** 남의 주소록을 건드리지 못하게 막는다. */
	private ShippingAddress findOwned(Long userId, Long addressId) {
		ShippingAddress address = shippingAddressRepository.findById(addressId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 배송지입니다. addressId={}", addressId);
					return new CustomException(AddressErrorCode.ADDRESS_404);
				});

		// LAZY 프록시의 id 는 DB 를 치지 않고 읽는다
		if (!address.getUser().getId().equals(userId)) {
			log.warn("본인의 배송지가 아닙니다. addressId={}, userId={}", addressId, userId);
			throw new CustomException(AddressErrorCode.ADDRESS_403);
		}

		return address;
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

}
