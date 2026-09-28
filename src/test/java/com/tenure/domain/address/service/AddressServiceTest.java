package com.tenure.domain.address.service;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.dto.request.ShippingAddressUpdateRequest;
import com.tenure.domain.address.dto.response.ShippingAddressIdResponse;
import com.tenure.domain.address.dto.response.ShippingAddressResponse;
import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.enums.DeliveryRequestType;
import com.tenure.domain.address.exception.AddressErrorCode;
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
	private static final Long OTHER_USER_ID = 2L;
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

	@Nested
	@DisplayName("배송지 수정")
	class Update {

		@Test
		@DisplayName("보낸 값으로 전체를 덮어쓴다")
		void update_validRequest_overwritesAll() {
			ShippingAddress address = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			ShippingAddressIdResponse response = addressService.update(USER_ID, ADDRESS_ID, updateRequest());

			assertThat(response.getId()).isEqualTo(ADDRESS_ID);
			assertThat(address.getRecipientName()).isEqualTo("김유진");
			assertThat(address.getPhone()).isEqualTo("010-9999-8888");
			assertThat(address.getZipcode()).isEqualTo("06158");
			assertThat(address.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 1");
			assertThat(address.getAddressDetail()).isEqualTo("3층");
			assertThat(address.getDeliveryRequestType()).isEqualTo(DeliveryRequestType.DIRECT);
			assertThat(address.getDeliveryRequest()).isEqualTo("부재 시 문 앞");
		}

		@Test
		@DisplayName("기본 배송지 여부는 수정이 건드리지 않는다")
		void update_validRequest_keepsDefaultFlag() {
			ShippingAddress address = address(true);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			addressService.update(USER_ID, ADDRESS_ID, updateRequest());

			// 기본 지정은 별도 엔드포인트다. 수정 본문에 isDefault 가 없다
			assertThat(address.isDefaultAddress()).isTrue();
		}

		@Test
		@DisplayName("없는 배송지면 ADDRESS_404 예외가 발생한다")
		void update_unknownAddress_throwsNotFound() {
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> addressService.update(USER_ID, ADDRESS_ID, updateRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(AddressErrorCode.ADDRESS_404);
		}

		@Test
		@DisplayName("남의 배송지면 ADDRESS_403 이고 값이 바뀌지 않는다")
		void update_othersAddress_throwsForbidden() {
			ShippingAddress address = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			assertThatThrownBy(() -> addressService.update(OTHER_USER_ID, ADDRESS_ID, updateRequest()))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(AddressErrorCode.ADDRESS_403);

			assertThat(address.getRecipientName()).isEqualTo("홍길동");
		}
	}

	@Nested
	@DisplayName("배송 요청사항")
	class DeliveryRequest {

		@Test
		@DisplayName("고정 문구를 고르면 직접 입력값을 버린다")
		void update_fixedType_dropsFreeText() {
			ShippingAddress address = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			addressService.update(USER_ID, ADDRESS_ID, updateRequest(
					DeliveryRequestType.AT_DOOR, "이전에 쓴 문장"));

			// 유형은 '문 앞에 놓아주세요'인데 본문이 남아 있으면 order_shipment 로 그대로 복사돼
			// 배송 기사에게 잘못된 문구가 간다
			assertThat(address.getDeliveryRequestType()).isEqualTo(DeliveryRequestType.AT_DOOR);
			assertThat(address.getDeliveryRequest()).isNull();
		}

		@Test
		@DisplayName("직접 입력이면 본문을 그대로 둔다")
		void update_directType_keepsFreeText() {
			ShippingAddress address = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			addressService.update(USER_ID, ADDRESS_ID, updateRequest(
					DeliveryRequestType.DIRECT, "배송 전에 미리 연락 바랍니다."));

			assertThat(address.getDeliveryRequest()).isEqualTo("배송 전에 미리 연락 바랍니다.");
		}

		@Test
		@DisplayName("유형을 고르지 않으면 둘 다 비운다")
		void update_noType_clearsBoth() {
			ShippingAddress address = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			addressService.update(USER_ID, ADDRESS_ID, updateRequest(null, "버려질 문장"));

			assertThat(address.getDeliveryRequestType()).isNull();
			assertThat(address.getDeliveryRequest()).isNull();
		}
	}

	@Nested
	@DisplayName("배송지 삭제")
	class Delete {

		@Test
		@DisplayName("기본이 아닌 배송지는 지운다")
		void delete_notDefault_removesRow() {
			ShippingAddress address = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			addressService.delete(USER_ID, ADDRESS_ID);

			// 하드 삭제다. order_shipment 가 스냅샷을 갖고 있어 과거 거래는 끊기지 않는다
			verify(shippingAddressRepository).delete(address);
		}

		@Test
		@DisplayName("기본 배송지는 ADDRESS_409_LAST_DEFAULT 로 거부한다")
		void delete_defaultAddress_throwsConflict() {
			ShippingAddress address = address(true);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			assertThatThrownBy(() -> addressService.delete(USER_ID, ADDRESS_ID))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(AddressErrorCode.ADDRESS_409_LAST_DEFAULT);

			verify(shippingAddressRepository, times(0)).delete(any());
		}

		@Test
		@DisplayName("없는 배송지면 ADDRESS_404 예외가 발생한다")
		void delete_unknownAddress_throwsNotFound() {
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> addressService.delete(USER_ID, ADDRESS_ID))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(AddressErrorCode.ADDRESS_404);
		}

		@Test
		@DisplayName("남의 배송지면 ADDRESS_403 이고 지우지 않는다")
		void delete_othersAddress_throwsForbidden() {
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address(false)));

			assertThatThrownBy(() -> addressService.delete(OTHER_USER_ID, ADDRESS_ID))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(AddressErrorCode.ADDRESS_403);

			verify(shippingAddressRepository, times(0)).delete(any());
		}
	}

	@Nested
	@DisplayName("기본 배송지 지정")
	class ChangeDefault {

		@Test
		@DisplayName("기존 기본을 내리고 지정한 배송지를 올린다")
		void changeDefault_otherAddress_movesDefault() {
			ShippingAddress target = address(false);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(target));

			addressService.changeDefault(USER_ID, ADDRESS_ID);

			verify(shippingAddressRepository).releaseDefault(USER_ID);
			assertThat(target.isDefaultAddress()).isTrue();
		}

		@Test
		@DisplayName("이미 기본이면 아무것도 하지 않는다")
		void changeDefault_alreadyDefault_doesNothing() {
			ShippingAddress address = address(true);
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address));

			addressService.changeDefault(USER_ID, ADDRESS_ID);

			// 내렸다 올리면 같은 행을 두 번 건드린다. 해제조차 하지 않는다
			verify(shippingAddressRepository, times(0)).releaseDefault(any());
			assertThat(address.isDefaultAddress()).isTrue();
		}


		@Test
		@DisplayName("남의 배송지면 ADDRESS_403 이다")
		void changeDefault_othersAddress_throwsForbidden() {
			given(shippingAddressRepository.findById(ADDRESS_ID)).willReturn(Optional.of(address(false)));

			assertThatThrownBy(() -> addressService.changeDefault(OTHER_USER_ID, ADDRESS_ID))
					.isInstanceOf(CustomException.class)
					.extracting(AddressServiceTest::errorCodeOf)
					.isEqualTo(AddressErrorCode.ADDRESS_403);

			verifyNoInteractions(addressMapper);
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
				"07027", "서울특별시 동작구 상도로 369", "104동 201호", null, null, isDefault);
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

	private static ShippingAddressUpdateRequest updateRequest() {
		return ShippingAddressUpdateRequest.builder()
				.recipientName("김유진")
				.phone("010-9999-8888")
				.zipcode("06158")
				.address("서울특별시 강남구 테헤란로 1")
				.addressDetail("3층")
				.deliveryRequestType(DeliveryRequestType.DIRECT)
				.deliveryRequest("부재 시 문 앞")
				.build();
	}

	private static ShippingAddressUpdateRequest updateRequest(
			DeliveryRequestType type, String deliveryRequest
	) {
		return ShippingAddressUpdateRequest.builder()
				.recipientName("김유진")
				.phone("010-9999-8888")
				.zipcode("06158")
				.address("서울특별시 강남구 테헤란로 1")
				.addressDetail("3층")
				.deliveryRequestType(type)
				.deliveryRequest(deliveryRequest)
				.build();
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((CustomException) e).getErrorCode();
	}
}
