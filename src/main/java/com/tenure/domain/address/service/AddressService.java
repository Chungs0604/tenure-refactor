package com.tenure.domain.address.service;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.entity.ShippingAddress;
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
				request.getDeliveryRequest(),
				asDefault
		);

		ShippingAddress saveAddress = shippingAddressRepository.save(address);

		return ShippingAddressIdResponse.builder()
				.id(saveAddress.getId())
				.build();
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", userId);
					return new CustomException(UserErrorCode.USER_404);
				});
	}

}
