package com.tenure.domain.address.service;

import com.tenure.domain.address.dto.request.ShippingAddressUpdateRequest;
import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 경로에서 나가는 쿼리 수를 센다.
 *
 * <p>배송지 목록은 사용자를 참조하지만 응답에 싣지 않는다. 매퍼가 {@code user} 를 건드리는 순간
 * 배송지 수만큼 SELECT 가 붙으므로(N+1) 실제 수치로 못박아 둔다.
 */
@DisplayName("배송지 조회 쿼리 수")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AddressQueryCountTest extends IntegrationTestSupport {

	@Autowired private AddressService addressService;
	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private EntityManagerFactory entityManagerFactory;

	private User owner;
	private Statistics statistics;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		owner = userRepository.save(User.signUp(
				"query-count-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "query-count-%s".formatted(tag), null, Gender.MALE, 170, 64));

		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@AfterEach
	void tearDown() {
		shippingAddressRepository.deleteAll(shippingAddressRepository.findAllOf(owner.getId()));
		userRepository.delete(owner);
	}

	@Test
	@DisplayName("목록 조회는 배송지가 몇 건이든 SELECT 1회다")
	void getAddresses_manyRows_singleSelect() {
		save("기본", true);
		for (int i = 1; i <= 4; i++) {
			save("추가" + i, false);
		}

		statistics.clear();
		assertThat(addressService.getAddresses(owner.getId())).hasSize(5);

		// 5건인데 6이 나오면 매퍼나 응답이 user 를 건드렸다는 뜻이다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("소유자 검사는 사용자를 다시 조회하지 않는다")
	void update_ownershipCheck_doesNotLoadUser() {
		ShippingAddress address = save("기본", true);

		statistics.clear();
		addressService.update(owner.getId(), address.getId(), updateRequest());

		// findOwned 가 address.getUser().getId() 로 검사한다. LAZY 프록시의 id 는 DB 를 치지 않으므로
		// SELECT 1(배송지) + UPDATE 1 뿐이다. 3이 나오면 소유자 검사마다 사용자 조회가 붙은 것이다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("기본 지정은 SELECT 1 · 벌크 UPDATE 1 · 엔티티 UPDATE 1 이다")
	void changeDefault_movesDefault_threeStatements() {
		save("기본", true);
		ShippingAddress target = save("후보", false);

		statistics.clear();
		addressService.changeDefault(owner.getId(), target.getId());

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("이미 기본이면 SELECT 1회로 끝난다")
	void changeDefault_alreadyDefault_singleSelect() {
		ShippingAddress current = save("기본", true);

		statistics.clear();
		addressService.changeDefault(owner.getId(), current.getId());

		// 내렸다 올리지 않으므로 해제 쿼리조차 나가지 않는다
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	private ShippingAddress save(String recipientName, boolean isDefault) {
		return shippingAddressRepository.save(ShippingAddress.register(
				owner, recipientName, "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, isDefault));
	}

	private static ShippingAddressUpdateRequest updateRequest() {
		return ShippingAddressUpdateRequest.builder()
				.recipientName("김유진")
				.phone("010-9999-8888")
				.zipcode("06158")
				.address("서울특별시 강남구 테헤란로 1")
				.addressDetail("3층")
				.build();
	}
}
