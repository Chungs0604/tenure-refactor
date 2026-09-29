package com.tenure.domain.user.service;

import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.entity.UserGradeHistory;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.repository.UserGradeHistoryRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserGradeService")
class UserGradeServiceTest {

	private static final Long USER_ID = 1L;

	@Mock private PostRepository postRepository;
	@Mock private UserGradeHistoryRepository userGradeHistoryRepository;

	@InjectMocks private UserGradeService userGradeService;

	@Captor private ArgumentCaptor<UserGradeHistory> historyCaptor;

	@Test
	@DisplayName("유효 OOTD 가 20개가 되면 레코드로 올린다")
	void evaluateOnPost_reachedThreshold_promotes() {
		User user = basicUser();
		given(postRepository.countValidOotd(eq(USER_ID), any(LocalDateTime.class))).willReturn(20);

		boolean promoted = userGradeService.evaluateOnPost(user);

		assertThat(promoted).isTrue();
		assertThat(user.getGrade()).isEqualTo(UserGrade.RECORD);
	}

	@Test
	@DisplayName("승격하면 보장 만료 시각을 30일 뒤로 둔다 — 재평가 배치의 대상 선별 기준이다")
	void evaluateOnPost_promoted_setsGuaranteeExpiry() {
		User user = basicUser();
		given(postRepository.countValidOotd(eq(USER_ID), any(LocalDateTime.class))).willReturn(25);

		userGradeService.evaluateOnPost(user);

		assertThat(user.getGradeExpiresAt())
				.isAfter(LocalDateTime.now().plusDays(29))
				.isBefore(LocalDateTime.now().plusDays(31));
	}

	@Test
	@DisplayName("승격 이력에 판정 근거를 남긴다 — 나중에 다시 세면 수치가 달라진다")
	void evaluateOnPost_promoted_recordsHistoryWithCount() {
		User user = basicUser();
		given(postRepository.countValidOotd(eq(USER_ID), any(LocalDateTime.class))).willReturn(21);

		userGradeService.evaluateOnPost(user);

		verify(userGradeHistoryRepository).save(historyCaptor.capture());
		UserGradeHistory history = historyCaptor.getValue();
		assertThat(history.getBeforeGrade()).isEqualTo(UserGrade.BASIC);
		assertThat(history.getAfterGrade()).isEqualTo(UserGrade.RECORD);
		assertThat(history.getValidOotdCount()).isEqualTo(21);
		assertThat(history.getWindowStart()).isBefore(history.getWindowEnd());
	}

	@Test
	@DisplayName("19개면 올리지 않고 이력도 남기지 않는다")
	void evaluateOnPost_belowThreshold_doesNotPromote() {
		User user = basicUser();
		given(postRepository.countValidOotd(eq(USER_ID), any(LocalDateTime.class))).willReturn(19);

		boolean promoted = userGradeService.evaluateOnPost(user);

		assertThat(promoted).isFalse();
		assertThat(user.getGrade()).isEqualTo(UserGrade.BASIC);
		verify(userGradeHistoryRepository, times(0)).save(any());
	}

	@Test
	@DisplayName("이미 레코드면 세지 않는다 — 보장 기간 중에는 수치가 떨어져도 등급을 유지한다")
	void evaluateOnPost_alreadyRecord_countsNothing() {
		User user = basicUser();
		user.promoteToRecord(LocalDateTime.now().plusDays(30));

		boolean promoted = userGradeService.evaluateOnPost(user);

		assertThat(promoted).isFalse();
		verifyNoInteractions(postRepository, userGradeHistoryRepository);
	}

	private static User basicUser() {
		User user = User.signUp("gildong@naver.com", "$2a$10$hashed", "홍길동",
				"821012345678", "Sujun", null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}
}
