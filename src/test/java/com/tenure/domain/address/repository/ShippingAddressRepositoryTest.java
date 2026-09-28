package com.tenure.domain.address.repository;

import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.RepositorySliceTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShippingAddressRepository")
class ShippingAddressRepositoryTest extends RepositorySliceTestSupport {

	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManager em;

	private User owner;
	private User stranger;

	@BeforeEach
	void setUp() {
		owner = userRepository.save(user("owner@tenure.local", "owner"));
		stranger = userRepository.save(user("stranger@tenure.local", "stranger"));
	}

	@Nested
	@DisplayName("findAllOf")
	class FindAllOf {

		@Test
		@DisplayName("기본 배송지가 맨 위, 그다음 최근 등록 순이다")
		void findAllOf_mixed_ordersByDefaultThenRecent() {
			// 등록 순서와 기대 순서를 다르게 둬야 정렬이 실제로 동작하는지 드러난다
			ShippingAddress oldest = save(owner, "오래된", true);
			ShippingAddress middle = save(owner, "중간", false);
			ShippingAddress newest = save(owner, "최근", false);
			stamp(oldest, "2026-09-01T10:00:00");
			stamp(middle, "2026-09-02T10:00:00");
			stamp(newest, "2026-09-03T10:00:00");

			List<ShippingAddress> found = shippingAddressRepository.findAllOf(owner.getId());

			assertThat(found).extracting(ShippingAddress::getRecipientName)
					.containsExactly("오래된", "최근", "중간");
		}

		@Test
		@DisplayName("다른 사용자의 배송지는 섞이지 않는다")
		void findAllOf_otherUsersRows_excluded() {
			save(owner, "내 것", true);
			save(stranger, "남의 것", true);

			List<ShippingAddress> found = shippingAddressRepository.findAllOf(owner.getId());

			assertThat(found).extracting(ShippingAddress::getRecipientName).containsExactly("내 것");
		}

		@Test
		@DisplayName("배송지가 없으면 빈 목록이다")
		void findAllOf_noRows_returnsEmpty() {
			assertThat(shippingAddressRepository.findAllOf(owner.getId())).isEmpty();
		}
	}

	@Nested
	@DisplayName("releaseDefault")
	class ReleaseDefault {

		@Test
		@DisplayName("기본 배송지 1건만 내리고 영향 행수를 돌려준다")
		void releaseDefault_hasDefault_updatesOnlyThatRow() {
			ShippingAddress target = save(owner, "기본", true);
			ShippingAddress other = save(owner, "일반", false);
			em.flush();
			em.clear();

			int affected = shippingAddressRepository.releaseDefault(owner.getId());

			assertThat(affected).isEqualTo(1);
			assertThat(reload(target).isDefaultAddress()).isFalse();
			assertThat(reload(other).isDefaultAddress()).isFalse();
		}

		@Test
		@DisplayName("다른 사용자의 기본 배송지는 건드리지 않는다")
		void releaseDefault_otherUser_untouched() {
			save(owner, "내 기본", true);
			ShippingAddress othersDefault = save(stranger, "남의 기본", true);
			em.flush();
			em.clear();

			shippingAddressRepository.releaseDefault(owner.getId());

			assertThat(reload(othersDefault).isDefaultAddress()).isTrue();
		}

		@Test
		@DisplayName("내릴 기본이 없으면 0건을 돌려준다")
		void releaseDefault_noDefault_returnsZero() {
			save(owner, "일반", false);
			em.flush();
			em.clear();

			assertThat(shippingAddressRepository.releaseDefault(owner.getId())).isZero();
		}

		@Test
		@DisplayName("벌크 UPDATE 는 영속성 컨텍스트를 갱신하지 않는다")
		void releaseDefault_bulkUpdate_leavesManagedEntityStale() {
			ShippingAddress managed = save(owner, "기본", true);
			em.flush();

			shippingAddressRepository.releaseDefault(owner.getId());

			// 1차 캐시의 엔티티는 여전히 true 다. 서비스가 이 값을 믿고 분기하면 틀린다 —
			// AddressService 는 해제 대상을 엔티티로 다시 읽지 않아 이 함정을 밟지 않는다
			assertThat(managed.isDefaultAddress()).isTrue();

			em.clear();
			assertThat(reload(managed).isDefaultAddress()).isFalse();
		}
	}

	private ShippingAddress save(User user, String recipientName, boolean isDefault) {
		return shippingAddressRepository.save(ShippingAddress.register(
				user, recipientName, "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, isDefault));
	}

	/** created_at 은 감사 컬럼이라 값을 고를 수 없다. 정렬 검증을 위해 네이티브로 덮어쓴다. */
	private void stamp(ShippingAddress address, String createdAt) {
		em.flush();
		em.createNativeQuery("update shipping_address set created_at = ? where id = ?")
				.setParameter(1, LocalDateTime.parse(createdAt))
				.setParameter(2, address.getId())
				.executeUpdate();
		em.clear();
	}

	private ShippingAddress reload(ShippingAddress address) {
		return shippingAddressRepository.findById(address.getId()).orElseThrow();
	}

	private static User user(String email, String nickname) {
		return User.signUp(email, "$2a$10$hashed", "홍길동", "821012345678",
				nickname, null, Gender.MALE, 170, 64);
	}
}
