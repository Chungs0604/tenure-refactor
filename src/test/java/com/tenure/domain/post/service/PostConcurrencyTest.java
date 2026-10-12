package com.tenure.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.enums.AspectRatio;
import com.tenure.domain.post.exception.PostErrorCode;
import com.tenure.domain.post.repository.PostLikeRepository;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.post.repository.PostSaveRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CustomException;
import com.tenure.support.IntegrationTestSupport;
import java.time.LocalDate;
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

/**
 * 좋아요·저장 카운터가 동시 요청에서도 정확한지 본다.
 *
 * <p>트랜잭션을 테스트에 걸지 않는다 — 걸면 두 호출이 한 트랜잭션을 공유해 검증 자체가
 * 사라진다. 그래서 롤백이 없고, 각 테스트가 자기 데이터를 직접 만들고 지운다
 * (OfferConcurrencyTest·AddressConcurrencyTest 와 같은 방식).
 */
@DisplayName("좋아요·저장 카운터 동시성")
class PostConcurrencyTest extends IntegrationTestSupport {

	@Autowired private PostService postService;
	@Autowired private PostRepository postRepository;
	@Autowired private PostLikeRepository postLikeRepository;
	@Autowired private PostSaveRepository postSaveRepository;
	@Autowired private UserRepository userRepository;

	private User author;
	private Post post;

	@BeforeEach
	void setUp() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		author = persistUser("author-%s@tenure.local".formatted(tag), "author-%s".formatted(tag));
		post = postRepository.save(Post.publish(author, LocalDate.now(), AspectRatio.RATIO_3_4, false));
	}

	@AfterEach
	void tearDown() {
		postLikeRepository.deleteAll(postLikeRepository.findAll().stream()
				.filter(like -> like.getPost().getId().equals(post.getId()))
				.toList());
		postSaveRepository.deleteAll(postSaveRepository.findAll().stream()
				.filter(save -> save.getPost().getId().equals(post.getId()))
				.toList());
		postRepository.delete(post);
		userRepository.deleteAll(userRepository.findAll().stream()
				.filter(u -> u.getId().equals(author.getId())
						|| (extraUserIds != null && extraUserIds.contains(u.getId())))
				.toList());
	}

	private List<Long> extraUserIds;

	@Test
	@DisplayName("서로 다른 사용자가 동시에 좋아요를 눌러도 like_count 는 정확히 2 증가한다 — lost update 가 나면 1만 반영된다")
	void like_concurrentDifferentUsers_increasesExactlyByTwo() throws Exception {
		User userA = persistUser("liker-a-%s@tenure.local".formatted(UUID.randomUUID()), "likerA-%s".formatted(UUID.randomUUID().toString().substring(0, 6)));
		User userB = persistUser("liker-b-%s@tenure.local".formatted(UUID.randomUUID()), "likerB-%s".formatted(UUID.randomUUID().toString().substring(0, 6)));
		extraUserIds = List.of(userA.getId(), userB.getId());

		Outcome outcome = runConcurrently(
				() -> postService.like(userA.getId(), post.getId()),
				() -> postService.like(userB.getId(), post.getId()));

		assertThat(outcome.failures()).as("실패한 요청").isEmpty();

		// 행을 잠그지 않고 더티체킹만으로 증감하면 두 트랜잭션이 같은 값을 읽어 1만 반영된다 —
		// DB 에서 다시 읽어 실제로 2 가 반영됐는지 확인한다
		Post reloaded = postRepository.findActive(post.getId()).orElseThrow();
		assertThat(reloaded.getLikeCount()).isEqualTo(2);

		long likeRows = postLikeRepository.findAll().stream()
				.filter(like -> like.getPost().getId().equals(post.getId()))
				.count();
		assertThat(likeRows).as("post_like 실제 행 수").isEqualTo(2);
	}

	@Test
	@DisplayName("같은 사용자가 동시에 두 번 좋아요해도 한 건만 성공하고, 진 쪽은 POST_409_LIKE 다")
	void like_concurrentSameUser_onlyOneSucceeds() throws Exception {
		User liker = persistUser("liker-%s@tenure.local".formatted(UUID.randomUUID()), "liker-%s".formatted(UUID.randomUUID().toString().substring(0, 6)));
		extraUserIds = List.of(liker.getId());

		Outcome outcome = runConcurrently(
				() -> postService.like(liker.getId(), post.getId()),
				() -> postService.like(liker.getId(), post.getId()));

		// uk_post_like 위반이 500 이 아니라 POST_409_LIKE 로 번역돼 올라왔는지가 핵심
		assertThat(outcome.succeeded()).as("성공한 요청 수").isEqualTo(1);
		assertThat(outcome.failures()).hasSize(1);
		assertThat(outcome.failures().getFirst())
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PostErrorCode.POST_409_LIKE);

		Post reloaded = postRepository.findActive(post.getId()).orElseThrow();
		assertThat(reloaded.getLikeCount()).as("딱 한 건만 반영된다").isEqualTo(1);

		long likeRows = postLikeRepository.findAll().stream()
				.filter(like -> like.getPost().getId().equals(post.getId()))
				.count();
		assertThat(likeRows).as("post_like 실제 행 수").isEqualTo(1);
	}

	@Test
	@DisplayName("서로 다른 사용자가 동시에 저장해도 save_count 는 정확히 2 증가한다")
	void save_concurrentDifferentUsers_increasesExactlyByTwo() throws Exception {
		User userA = persistUser("saver-a-%s@tenure.local".formatted(UUID.randomUUID()), "saverA-%s".formatted(UUID.randomUUID().toString().substring(0, 6)));
		User userB = persistUser("saver-b-%s@tenure.local".formatted(UUID.randomUUID()), "saverB-%s".formatted(UUID.randomUUID().toString().substring(0, 6)));
		extraUserIds = List.of(userA.getId(), userB.getId());

		Outcome outcome = runConcurrently(
				() -> postService.save(userA.getId(), post.getId()),
				() -> postService.save(userB.getId(), post.getId()));

		assertThat(outcome.failures()).as("실패한 요청").isEmpty();

		Post reloaded = postRepository.findActive(post.getId()).orElseThrow();
		assertThat(reloaded.getSaveCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("같은 사용자가 동시에 두 번 저장해도 한 건만 성공하고, 진 쪽은 POST_409_SAVE 다")
	void save_concurrentSameUser_onlyOneSucceeds() throws Exception {
		User saver = persistUser("saver-%s@tenure.local".formatted(UUID.randomUUID()), "saver-%s".formatted(UUID.randomUUID().toString().substring(0, 6)));
		extraUserIds = List.of(saver.getId());

		Outcome outcome = runConcurrently(
				() -> postService.save(saver.getId(), post.getId()),
				() -> postService.save(saver.getId(), post.getId()));

		assertThat(outcome.succeeded()).as("성공한 요청 수").isEqualTo(1);
		assertThat(outcome.failures()).hasSize(1);
		assertThat(outcome.failures().getFirst())
				.isInstanceOf(CustomException.class)
				.extracting(e -> ((CustomException) e).getErrorCode())
				.isEqualTo(PostErrorCode.POST_409_SAVE);

		Post reloaded = postRepository.findActive(post.getId()).orElseThrow();
		assertThat(reloaded.getSaveCount()).as("딱 한 건만 반영된다").isEqualTo(1);
	}

	/** 두 작업을 같은 순간에 출발시키고 성공 수·실패(예외)를 모은다. */
	private Outcome runConcurrently(Runnable first, Runnable second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicInteger succeeded = new AtomicInteger();
		List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			for (Runnable task : List.of(first, second)) {
				pool.submit(() -> {
					try {
						start.await();
						task.run();
						succeeded.incrementAndGet();
					} catch (Throwable e) {
						failures.add(e);
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

	private User persistUser(String email, String nickname) {
		return userRepository.save(User.signUp(email, "$2a$10$hashed", "홍길동",
				"821012345678", nickname, null, Gender.MALE, 170, 64));
	}

	private record Outcome(int succeeded, List<Throwable> failures) {
	}
}
