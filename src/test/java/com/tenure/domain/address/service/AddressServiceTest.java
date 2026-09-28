package com.tenure.domain.address.service;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.mapper.AddressMapper;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressService")
class AddressServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long ADDRESS_ID = 10L;

	@Mock private ShippingAddressRepository shippingAddressRepository;
	@Mock private UserRepository userRepository;

	/** mock 하지 않는다 — mock 하면 엔티티에 필드를 넣고 응답에 안 실어도 테스트가 통과한다. */
	@Spy private AddressMapper addressMapper = Mappers.getMapper(AddressMapper.class);

	@InjectMocks private AddressService addressService;

	@Captor private ArgumentCaptor<ShippingAddress> addressCaptor;

	@Nested
	@DisplayName("배송지 목록")
	class GetAddresses {

		@Test
		@DisplayName("주소록을 그대로 내려준다")
		void getAddresses_hasAddresses_returnsAll() {
			given(shippingAddressRepository.findAllOf(USER_ID))
					.willReturn(List.of(address(true), address(false)));

			List<ShippingAddressResponse> responses = addressService.getAddresses(USER_ID);

			// 정렬은 쿼리가 맡는다. 서비스는 순서를 다시 만들지 않는다
			assertThat(responses).hasSize(2);
			assertThat(responses.get(0).getIsDefault()).isTrue();
			assertThat(responses.get(0).getRecipientName()).isEqualTo("홍길동");
		}

		@Test
		@DisplayName("배송지가 없으면 빈 목록이다")
		void getAddresses_noAddress_returnsEmpty() {
			given(shippingAddressRepository.findAllOf(USER_ID)).willReturn(List.of());

			assertThat(addressService.getAddresses(USER_ID)).isEmpty();
		}
	}

	@Nested
	@DisplayName("배송지 추가")
	class Create {

		@Test
		@DisplayName("기본으로 넣지 않으면 기존 기본을 건드리지 않는다")
		void create_withoutDefaultFlag_keepsCurrentDefault() {
			givenUserExists();
			givenSaveAssignsId();

			ShippingAddressIdResponse response = addressService.create(USER_ID, createRequest(null));

			assertThat(response.getId()).isEqualTo(ADDRESS_ID);
			verify(shippingAddressRepository).save(addressCaptor.capture());
			assertThat(addressCaptor.getValue().isDefaultAddress()).isFalse();
			verify(shippingAddressRepository, times(0)).releaseDefault(any());
		}

		@Test
		@DisplayName("기본으로 추가하면 기존 기본 배송지를 먼저 내린다")
		void create_asDefault_releasesPreviousDefault() {
			givenUserExists();
			givenSaveAssignsId();

			addressService.create(USER_ID, createRequest(true));

			// 해제가 INSERT 보다 먼저 DB 에 닿아야 uk_shipping_address_default 에 걸리지 않는다
			InOrder order = inOrder(shippingAddressRepository);
			order.verify(shippingAddressRepository).releaseDefault(USER_ID);
			order.verify(shippingAddressRepository).save(addressCaptor.capture());
			assertThat(addressCaptor.getValue().isDefaultAddress()).isTrue();
		}

		@Test
		@DisplayName("없는 사용자면 USER_404 이고 저장하지 않는다")
		void create_unknownUser_throwsNotFound() {
			given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> addressService.create(USER_ID, createRequest(null)))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(UserErrorCode.USER_404);

			verify(shippingAddressRepository, times(0)).save(any());
		}

		private void givenSaveAssignsId() {
			given(shippingAddressRepository.save(any(ShippingAddress.class))).willAnswer(invocation -> {
				ShippingAddress saved = invocation.getArgument(0);
				ReflectionTestUtils.setField(saved, "id", ADDRESS_ID);
				return saved;
			});
		}
	}

	private void givenUserExists() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(owner()));
	}

	/** 주소록의 주인. id 만 쓰이므로 프로필 값은 최소로 채운다. */
	private static User owner() {
		User user = User.signUp("gildong@naver.com", "$2a$10$hashed", "홍길동",
				"821012345678", "Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private static ShippingAddress address(boolean isDefault) {
		ShippingAddress address = ShippingAddress.register(owner(), "홍길동", "010-1234-5678",
				"07027", "서울특별시 동작구 상도로 369", "104동 201호", null, isDefault);
		ReflectionTestUtils.setField(address, "id", ADDRESS_ID);
		return address;
	}

	private static ShippingAddressCreateRequest createRequest(Boolean isDefault) {
		return ShippingAddressCreateRequest.builder()
				.recipientName("홍길동")
				.phone("010-1234-5678")
				.zipcode("07027")
				.address("서울특별시 동작구 상도로 369")
				.addressDetail("104동 201호")
				.isDefault(isDefault)
				.build();
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
