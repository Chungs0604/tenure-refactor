package com.tenure.domain.report.service;

import com.tenure.domain.item.repository.ItemRepository;
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
import com.tenure.domain.user.exception.UserErrorCode;
import com.tenure.domain.user.repository.UserRepository;
import com.tenure.global.exception.CommonErrorCode;
import com.tenure.global.exception.CustomException;
import com.tenure.global.logging.Loggable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시물·상품 신고 접수. 접수까지만 담는다 — 운영자 처리 흐름은 범위 밖.
 *
 * 예외를 던지기 전에 판단 근거를 남김 — ErrorCode 자체는 ServiceLoggingAspect 가 찍음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	/**
	 * 신고 드롭다운. 대상에 맞지 않는 reason 이면 COMMON_400 — targetType=USER 는 이 API 에서
	 * 받지 않으므로 여기 두지 않는다.
	 */
	private static final Map<ReportTargetType, Set<ReportReason>> ALLOWED_REASONS = new EnumMap<>(Map.of(
			ReportTargetType.POST, EnumSet.of(
					ReportReason.NOT_WORN_OOTD, ReportReason.INAPPROPRIATE, ReportReason.STOLEN_PHOTO,
					ReportReason.SPAM, ReportReason.PRIVACY_EXPOSURE, ReportReason.IRRELEVANT),
			ReportTargetType.ITEM, EnumSet.of(
					ReportReason.COUNTERFEIT, ReportReason.INFO_MISMATCH, ReportReason.STOLEN_PHOTO,
					ReportReason.FRAUD_EXTERNAL_TRADE, ReportReason.PROHIBITED_ITEM, ReportReason.INAPPROPRIATE)
	));

	private final ReportRepository reportRepository;
	private final UserRepository userRepository;
	private final PostRepository postRepository;
	private final ItemRepository itemRepository;
	private final ReportMapper reportMapper;

	@Transactional
	@Loggable("신고 접수")
	public ReportResponse submit(Long reporterId, ReportRequest request) {
		ReportTargetType targetType = request.getTargetType();

		if (targetType == ReportTargetType.USER) {
			log.warn("이 API 는 USER 신고를 받지 않음. reporterId={}", reporterId);
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}

		if (!ALLOWED_REASONS.get(targetType).contains(request.getReason())) {
			log.warn("targetType 에 허용되지 않는 reason 임. targetType={} reason={}",
					targetType, request.getReason());
			throw new CustomException(CommonErrorCode.BAD_REQUEST);
		}

		ensureTargetExists(targetType, request.getTargetId());

		User reporter = userRepository.findById(reporterId)
				.orElseThrow(() -> {
					log.warn("존재하지 않는 사용자입니다. userId={}", reporterId);
					return new CustomException(UserErrorCode.USER_404);
				});

		Report saved = reportRepository.save(Report.submit(
				reporter, targetType, request.getTargetId(), request.getReason(), request.getDetail()));

		return reportMapper.toResponse(saved);
	}

	/** 신고 대상이 실제로 존재하는지(soft-delete 제외) 확인함. */
	private void ensureTargetExists(ReportTargetType targetType, Long targetId) {
		boolean exists = switch (targetType) {
			case POST -> postRepository.findActive(targetId).isPresent();
			case ITEM -> itemRepository.findActive(targetId).isPresent();
			case USER -> false; // 위에서 이미 거름 — switch 완전성을 위해 둠
		};

		if (!exists) {
			log.info("없거나 삭제된 대상을 신고하려 함. targetType={} targetId={}", targetType, targetId);
			throw new CustomException(ReportErrorCode.REPORT_404_TARGET);
		}
	}
}
