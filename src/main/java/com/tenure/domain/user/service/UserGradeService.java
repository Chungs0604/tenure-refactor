package com.tenure.domain.user.service;

import com.tenure.domain.post.repository.PostRepository;
import com.tenure.domain.user.entity.User;
import com.tenure.domain.user.entity.UserGradeHistory;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.repository.UserGradeHistoryRepository;
import com.tenure.global.logging.Loggable;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레코드 등급 판정. 조건이 '직전 30일 유효 OOTD 20개' 라 게시물 수를 세야 해서 엔티티가 할 수 없다.
 *
 * 게시 도메인에 두지 않은 이유 — 승격 조건·보장 기간·이력 기록이 모두 등급 규칙이고, 보장 만료
 * 시점의 재평가(강등)도 같은 규칙을 쓴다. post 에 두면 강등 배치가 post 도메인을 호출하게 된다.
 *
 * 승격은 배치가 아니라 게시 즉시 판정한다 — 20개째를 올린 사용자가 판매 전환 화면에서 바로
 * 레코드 혜택(수수료 3% · 수수료 부담 선택)을 봐야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserGradeService {

	/** 평가 구간. policies.record_grade_window_days */
	private static final int WINDOW_DAYS = 30;

	/** 승격 기준 개수. policies.record_grade_threshold */
	private static final int THRESHOLD = 20;

	/** 승격 후 보장 기간. policies.record_grade_guarantee_days */
	private static final int GUARANTEE_DAYS = 30;

	private final PostRepository postRepository;
	private final UserGradeHistoryRepository userGradeHistoryRepository;

	/**
	 * 게시 직후 승격 판정. 게시 트랜잭션 안에서 호출된다.
	 *
	 * 이미 레코드면 세지 않는다 — 보장 기간 중에는 수치가 떨어져도 등급을 유지하고, 재평가는
	 * grade_expires_at 이 지난 뒤 배치가 한다.
	 *
	 * 반환값 — 이 게시로 승격됐으면 true. 화면이 승격 안내를 띄우는 근거다
	 */
	@Transactional
	@Loggable("레코드 승격 판정")
	public boolean evaluateOnPost(User user) {

		if (user.isRecordGrade()) {
			return false;
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime windowStart = now.minusDays(WINDOW_DAYS);

		int validOotdCount = postRepository.countValidOotd(user.getId(), windowStart);
		if (validOotdCount < THRESHOLD) {
			return false;
		}

		UserGrade beforeGrade = user.getGrade();
		user.promoteToRecord(now.plusDays(GUARANTEE_DAYS));

		// 판정 근거를 함께 남긴다 — 나중에 다시 세면 그 사이 삭제된 게시물 때문에 수치가 달라진다
		userGradeHistoryRepository.save(UserGradeHistory.of(
				user, beforeGrade, UserGrade.RECORD,
				windowStart.toLocalDate(), now.toLocalDate(), validOotdCount
		));

		log.info("레코드 등급으로 승격했습니다. userId={}, validOotdCount={}",
				user.getId(), validOotdCount);
		return true;
	}
}
