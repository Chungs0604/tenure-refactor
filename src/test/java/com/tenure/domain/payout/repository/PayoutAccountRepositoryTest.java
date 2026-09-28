package com.tenure.domain.payout.repository;

import com.tenure.domain.payout.entity.PayoutAccount;
import com.tenure.domain.payout.enums.BankCode;
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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PayoutAccountRepository")
class PayoutAccountRepositoryTest extends RepositorySliceTestSupport {

	@Autowired private PayoutAccountRepository payoutAccountRepository;
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
		@DisplayName("기본 계좌가 맨 위, 그다음 최근 등록 순이다")
		void findAllOf_mixed_ordersByDefaultThenRecent() {
			// 등록 순서와 기대 순서를 다르게 둬야 정렬이 실제로 동작하는지 드러난다
			PayoutAccount oldest = save(owner, "오래된", true);
			PayoutAccount middle = save(owner, "중간", false);
			PayoutAccount newest = save(owner, "최근", false);
			stamp(oldest, "2026-09-01T10:00:00");
			stamp(middle, "2026-09-02T10:00:00");
			stamp(newest, "2026-09-03T10:00:00");

			List<PayoutAccount> found = payoutAccountRepository.findAllOf(owner.getId());

			assertThat(found).extracting(PayoutAccount::getAccountHolder)
					.containsExactly("오래된", "최근", "중간");
		}

		@Test
		@DisplayName("다른 사용자의 계좌는 섞이지 않는다")
		void findAllOf_otherUsersRows_excluded() {
			save(owner, "내 것", true);
			save(stranger, "남의 것", true);

			List<PayoutAccount> found = payoutAccountRepository.findAllOf(owner.getId());

			assertThat(found).extracting(PayoutAccount::getAccountHolder).containsExactly("내 것");
		}

		@Test
		@DisplayName("계좌가 없으면 빈 목록이다")
		void findAllOf_noRows_returnsEmpty() {
			assertThat(payoutAccountRepository.findAllOf(owner.getId())).isEmpty();
		}
	}

	@Nested
	@DisplayName("existsByUserId")
	class ExistsByUserId {

		@Test
		@DisplayName("계좌가 없으면 false — 다음 등록이 기본이 된다")
		void existsByUserId_noRows_returnsFalse() {
			assertThat(payoutAccountRepository.existsByUserId(owner.getId())).isFalse();
		}

		@Test
		@DisplayName("남의 계좌만 있어도 false 다")
		void existsByUserId_onlyOtherUsersRows_returnsFalse() {
			save(stranger, "남의 것", true);

			assertThat(payoutAccountRepository.existsByUserId(owner.getId())).isFalse();
		}

		@Test
		@DisplayName("기본이 아닌 계좌라도 있으면 true 다")
		void existsByUserId_hasNonDefaultRow_returnsTrue() {
			save(owner, "일반", false);

			assertThat(payoutAccountRepository.existsByUserId(owner.getId())).isTrue();
		}
	}

	@Nested
	@DisplayName("releaseDefault")
	class ReleaseDefault {

		@Test
		@DisplayName("기본 계좌 1건만 내리고 영향 행수를 돌려준다")
		void releaseDefault_hasDefault_updatesOnlyThatRow() {
			PayoutAccount target = save(owner, "기본", true);
			PayoutAccount other = save(owner, "일반", false);
			em.flush();
			em.clear();

			int affected = payoutAccountRepository.releaseDefault(owner.getId());

			assertThat(affected).isEqualTo(1);
			assertThat(reload(target).isDefaultAccount()).isFalse();
			assertThat(reload(other).isDefaultAccount()).isFalse();
		}

		@Test
		@DisplayName("다른 사용자의 기본 계좌는 건드리지 않는다")
		void releaseDefault_otherUser_untouched() {
			save(owner, "내 기본", true);
			PayoutAccount othersDefault = save(stranger, "남의 기본", true);
			em.flush();
			em.clear();

			payoutAccountRepository.releaseDefault(owner.getId());

			assertThat(reload(othersDefault).isDefaultAccount()).isTrue();
		}

		@Test
		@DisplayName("내릴 기본이 없으면 0건을 돌려준다 — 계좌는 0건도 정상이다")
		void releaseDefault_noDefault_returnsZero() {
			assertThat(payoutAccountRepository.releaseDefault(owner.getId())).isZero();
		}
	}

	@Nested
	@DisplayName("uk_payout_account_default")
	class UniqueDefault {

		@Test
		@DisplayName("기본 계좌가 둘이면 DB 가 막는다")
		void save_twoDefaults_violatesUniqueIndex() {
			save(owner, "기본", true);

			// IDENTITY 전략이라 persist 시점에 INSERT 가 나간다 — flush 를 기다리지 않고 여기서 터진다
			assertThatThrownBy(() -> save(owner, "또 기본", true))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("기본이 아닌 계좌는 몇 개든 쌓인다 — NULL 중복은 유니크가 허용한다")
		void save_manyNonDefaults_allowed() {
			save(owner, "일반1", false);
			save(owner, "일반2", false);
			save(owner, "일반3", false);
			em.flush();

			assertThat(payoutAccountRepository.findAllOf(owner.getId())).hasSize(3);
		}

		@Test
		@DisplayName("사용자가 다르면 각자 기본을 가질 수 있다")
		void save_defaultPerUser_allowed() {
			save(owner, "내 기본", true);
			save(stranger, "남의 기본", true);
			em.flush();

			assertThat(payoutAccountRepository.findAllOf(owner.getId())).hasSize(1);
			assertThat(payoutAccountRepository.findAllOf(stranger.getId())).hasSize(1);
		}
	}

	private PayoutAccount save(User user, String accountHolder, boolean isDefault) {
		return payoutAccountRepository.save(PayoutAccount.register(
				user, BankCode.TOSS_BANK, accountHolder, "123456-789012", isDefault));
	}

	/** created_at 은 감사 컬럼이라 값을 고를 수 없다. 정렬 검증을 위해 네이티브로 덮어쓴다. */
	private void stamp(PayoutAccount account, String createdAt) {
		em.flush();
		em.createNativeQuery("update payout_account set created_at = ? where id = ?")
				.setParameter(1, LocalDateTime.parse(createdAt))
				.setParameter(2, account.getId())
				.executeUpdate();
		em.clear();
	}

	private PayoutAccount reload(PayoutAccount account) {
		return payoutAccountRepository.findById(account.getId()).orElseThrow();
	}

	private static User user(String email, String nickname) {
		return User.signUp(email, "$2a$10$hashed", "홍길동", "821012345678",
				nickname, null, Gender.MALE, 170, 64);
	}
}
