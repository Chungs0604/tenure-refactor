package com.tenure.domain.address.service;

import com.tenure.domain.address.dto.request.ShippingAddressCreateRequest;
import com.tenure.domain.address.entity.ShippingAddress;
import com.tenure.domain.address.repository.ShippingAddressRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기본 배송지 단일성이 동시 요청에서도 지켜지는지 본다.
 *
 * <p>트랜잭션을 테스트에 걸지 않는다 — 걸면 모든 호출이 한 트랜잭션을 공유해 검증 자체가 사라진다.
 * 그래서 롤백이 없고, 컨테이너를 재사용하므로 각 테스트가 자기 데이터를 지운다.
 */
@DisplayName("기본 배송지 동시성")
class AddressConcurrencyTest extends IntegrationTestSupport {

	@Autowired private AddressService addressService;
	@Autowired private ShippingAddressRepository shippingAddressRepository;
	@Autowired private UserRepository userRepository;

	private User owner;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		owner = userRepository.save(User.signUp(
				"concurrent-%s@tenure.local".formatted(tag), "$2a$10$hashed", "홍길동",
				"821012345678", "concurrent-%s".formatted(tag), null, Gender.MALE, 170, 64));

		// 가입이 만드는 기본 배송지를 그대로 흉내 낸다
		save("기본", true);
	}

	@AfterEach
	void tearDown() {
		shippingAddressRepository.deleteAll(shippingAddressRepository.findAllOf(owner.getId()));
		userRepository.delete(owner);
	}

	@Test
	@DisplayName("서로 다른 배송지를 동시에 기본으로 지정해도 기본은 1건이다")
	void changeDefault_concurrentDifferentTargets_keepsSingleDefault() throws Exception {
		ShippingAddress first = save("후보1", false);
		ShippingAddress second = save("후보2", false);

		Outcome outcome = runConcurrently(
				() -> addressService.changeDefault(owner.getId(), first.getId()),
				() -> addressService.changeDefault(owner.getId(), second.getId()));

		assertThat(defaults()).hasSize(1);
		assertThat(outcome.succeeded()).isPositive();
	}

	@Test
	@DisplayName("같은 배송지를 연속 두 번 지정해도 기본은 1건이고 그 배송지가 기본이다")
	void changeDefault_concurrentSameTarget_keepsSingleDefault() throws Exception {
		ShippingAddress target = save("후보", false);

		runConcurrently(
				() -> addressService.changeDefault(owner.getId(), target.getId()),
				() -> addressService.changeDefault(owner.getId(), target.getId()));

		List<ShippingAddress> defaults = defaults();
		assertThat(defaults).hasSize(1);
		assertThat(defaults.getFirst().getId()).isEqualTo(target.getId());
	}

	@Test
	@DisplayName("기본으로 추가하는 요청이 동시에 둘 들어와도 기본은 1건이다")
	void create_concurrentAsDefault_keepsSingleDefault() throws Exception {
		Outcome outcome = runConcurrently(
				() -> addressService.create(owner.getId(), createRequest("동시1")),
				() -> addressService.create(owner.getId(), createRequest("동시2")));

		assertThat(defaults()).hasSize(1);
		assertThat(outcome.succeeded()).isPositive();
	}

	@Test
	@DisplayName("동시 요청이 유니크 위반으로 실패하지 않는다")
	void changeDefault_concurrent_bothSucceed() throws Exception {
		ShippingAddress first = save("후보1", false);
		ShippingAddress second = save("후보2", false);

		Outcome outcome = runConcurrently(
				() -> addressService.changeDefault(owner.getId(), first.getId()),
				() -> addressService.changeDefault(owner.getId(), second.getId()));

		// releaseDefault 의 벌크 UPDATE 가 현재 기본 행에 X 락을 잡아 뒤 요청이 대기한다.
		// 이 단정이 깨지면(드물게 실제 데드락이 나면) AddressService.changeDefault() 가
		// DataIntegrityViolationException/CannotAcquireLockException 을
		// ADDRESS_409_DEFAULT_CONFLICT 로 번역해 올린다 — 500 이 아니라 CustomException 이 된다
		assertThat(outcome.failures())
				.as("실패한 요청의 예외")
				.isEmpty();
	}

	/** 두 작업을 같은 순간에 출발시키고 결과를 모은다. */
	private Outcome runConcurrently(Runnable first, Runnable second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicInteger succeeded = new AtomicInteger();
		List<String> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			for (Runnable task : List.of(first, second)) {
				pool.submit(() -> {
					try {
						start.await();
						task.run();
						succeeded.incrementAndGet();
					} catch (Exception e) {
						failures.add(e.getClass().getSimpleName() + ": " + e.getMessage());
					} finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			assertThat(done.await(10, TimeUnit.SECONDS)).as("10초 안에 끝난다").isTrue();
		}

		return new Outcome(succeeded.get(), List.copyOf(failures));
	}

	private List<ShippingAddress> defaults() {
		return shippingAddressRepository.findAllOf(owner.getId()).stream()
				.filter(ShippingAddress::isDefaultAddress)
				.toList();
	}

	private ShippingAddress save(String recipientName, boolean isDefault) {
		return shippingAddressRepository.save(ShippingAddress.register(
				owner, recipientName, "010-1234-5678", "07027",
				"서울특별시 동작구 상도로 369", "104동 201호", null, null, isDefault));
	}

	private static ShippingAddressCreateRequest createRequest(String recipientName) {
		return ShippingAddressCreateRequest.builder()
				.recipientName(recipientName)
				.phone("010-1234-5678")
				.zipcode("07027")
				.address("서울특별시 동작구 상도로 369")
				.isDefault(true)
				.build();
	}

	private record Outcome(int succeeded, List<String> failures) {}
}
