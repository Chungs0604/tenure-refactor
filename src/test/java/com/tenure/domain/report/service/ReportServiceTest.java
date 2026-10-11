package com.tenure.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tenure.domain.item.entity.Item;
import com.tenure.domain.item.repository.ItemRepository;
import com.tenure.domain.post.entity.Post;
import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.report.dto.request.ReportRequest;
import com.tenure.domain.report.dto.response.ReportResponse;
import com.tenure.domain.report.entity.Report;
import com.tenure.domain.report.enums.ReportReason;
import com.tenure.domain.report.enums.ReportTargetType;
import com.tenure.domain.report.exception.ReportErrorCode;
import com.tenure.domain.report.mapper.ReportMapper;
import com.tenure.domain.report.repository.ReportRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

	private static final Long REPORTER_ID = 1L;
	private static final Long TARGET_ID = 10L;

	@Mock private ReportRepository reportRepository;
	@Mock private UserRepository userRepository;
	@Mock private PostRepository postRepository;
	@Mock private ItemRepository itemRepository;

	/** mock 하지 않음 — mock 하면 엔티티에 필드를 넣고 응답에 안 실어도 테스트가 통과함 */
	@Spy private ReportMapper reportMapper = Mappers.getMapper(ReportMapper.class);

	@InjectMocks private ReportService reportService;

	@Nested
	@DisplayName("정상 접수")
	class Submit {

		@Test
		@DisplayName("POST 대상·허용된 reason 이면 접수된다")
		void submit_postWithAllowedReason_succeeds() {
			given(postRepository.findActive(TARGET_ID)).willReturn(Optional.of(mock(Post.class)));
			given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(user(REPORTER_ID)));
			given(reportRepository.save(any(Report.class))).willAnswer(invocation -> {
				Report report = invocation.getArgument(0);
				ReflectionTestUtils.setField(report, "id", 100L);
				return report;
			});

			ReportResponse response = reportService.submit(REPORTER_ID,
					request(ReportTargetType.POST, ReportReason.SPAM, null));

			assertThat(response.getId()).isEqualTo(100L);
		}

		@Test
		@DisplayName("ITEM 대상·허용된 reason 이면 접수된다")
		void submit_itemWithAllowedReason_succeeds() {
			given(itemRepository.findActive(TARGET_ID)).willReturn(Optional.of(mock(Item.class)));
			given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(user(REPORTER_ID)));
			given(reportRepository.save(any(Report.class))).willAnswer(invocation -> {
				Report report = invocation.getArgument(0);
				ReflectionTestUtils.setField(report, "id", 101L);
				return report;
			});

			ReportResponse response = reportService.submit(REPORTER_ID,
					request(ReportTargetType.ITEM, ReportReason.COUNTERFEIT, "가품 의심됩니다."));

			assertThat(response.getId()).isEqualTo(101L);
		}
	}

	@Nested
	@DisplayName("targetType 검증")
	class TargetTypeValidation {

		@Test
		@DisplayName("USER 신고는 COMMON_400 이고 아무것도 조회하지 않는다")
		void submit_userTargetType_throwsCommon400() {
			assertThatThrownBy(() -> reportService.submit(REPORTER_ID,
					request(ReportTargetType.USER, ReportReason.SPAM, null)))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(CommonErrorCode.BAD_REQUEST);

			verifyNoInteractions(postRepository, itemRepository, userRepository, reportRepository);
		}

		@Test
		@DisplayName("POST 에 ITEM 전용 reason 을 쓰면 COMMON_400 이고 대상을 조회하지 않는다")
		void submit_itemReasonForPostTarget_throwsCommon400() {
			assertThatThrownBy(() -> reportService.submit(REPORTER_ID,
					request(ReportTargetType.POST, ReportReason.COUNTERFEIT, null)))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(CommonErrorCode.BAD_REQUEST);

			verifyNoInteractions(postRepository, itemRepository);
		}

		@Test
		@DisplayName("ITEM 에 POST 전용 reason 을 쓰면 COMMON_400 이다")
		void submit_postReasonForItemTarget_throwsCommon400() {
			assertThatThrownBy(() -> reportService.submit(REPORTER_ID,
					request(ReportTargetType.ITEM, ReportReason.NOT_WORN_OOTD, null)))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(CommonErrorCode.BAD_REQUEST);

			verifyNoInteractions(postRepository, itemRepository);
		}
	}

	@Nested
	@DisplayName("대상 존재 확인")
	class TargetExistence {

		@Test
		@DisplayName("없거나 삭제된 POST 면 REPORT_404_TARGET 이다")
		void submit_postNotFound_throwsReportTargetNotFound() {
			given(postRepository.findActive(TARGET_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> reportService.submit(REPORTER_ID,
					request(ReportTargetType.POST, ReportReason.SPAM, null)))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(ReportErrorCode.REPORT_404_TARGET);

			verifyNoInteractions(userRepository, reportRepository);
		}

		@Test
		@DisplayName("없거나 삭제된 ITEM 이면 REPORT_404_TARGET 이다")
		void submit_itemNotFound_throwsReportTargetNotFound() {
			given(itemRepository.findActive(TARGET_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> reportService.submit(REPORTER_ID,
					request(ReportTargetType.ITEM, ReportReason.COUNTERFEIT, null)))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(ReportErrorCode.REPORT_404_TARGET);

			verifyNoInteractions(userRepository, reportRepository);
		}

		@Test
		@DisplayName("신고자가 없으면 USER_404 이다")
		void submit_reporterNotFound_throwsUserNotFound() {
			given(postRepository.findActive(TARGET_ID)).willReturn(Optional.of(mock(Post.class)));
			given(userRepository.findById(REPORTER_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> reportService.submit(REPORTER_ID,
					request(ReportTargetType.POST, ReportReason.SPAM, null)))
					.isInstanceOf(CustomException.class)
					.extracting(e -> ((CustomException) e).getErrorCode())
					.isEqualTo(UserErrorCode.USER_404);

			verifyNoInteractions(reportRepository);
		}
	}

	private static ReportRequest request(ReportTargetType targetType, ReportReason reason, String detail) {
		return ReportRequest.builder()
				.targetType(targetType)
				.targetId(TARGET_ID)
				.reason(reason)
				.detail(detail)
				.build();
	}

	private static User user(Long id) {
		User user = User.signUp("user" + id + "@tenure.app", "$2a$10$hashed", "홍길동",
				"821012345678", "nick" + id, null, Gender.MALE, 170, 64);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
