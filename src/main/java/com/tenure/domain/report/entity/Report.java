package com.tenure.domain.report.entity;

import com.tenure.domain.report.enums.ReportReason;
import com.tenure.domain.report.enums.ReportTargetType;
import com.tenure.domain.user.entity.User;
import com.tenure.global.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시물·상품·사용자 신고. ReportReason 10종의 발생 근거.
 */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Report extends BaseCreatedEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 신고자 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_id", nullable = false)
	private User reporter;

	/** 신고 진입점 3종 */
	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private ReportTargetType targetType;

	/** 다형 참조라 FK 없음 */
	@Column(name = "target_id", nullable = false)
	private Long targetId;

	/** '신고 사유를 선택해주세요.' 드롭다운 */
	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 40)
	private ReportReason reason;

	/** 상세 사유 입력란. 화면 미확인 */
	@Column(name = "detail", length = 500)
	private String detail;

	/**
	 * 신고 접수. 접수까지만 담는다 — 처리 상태는 없음.
	 *
	 * targetType·reason 조합의 유효성, 대상 존재 여부는 Service 가 먼저 가린다.
	 */
	public static Report submit(
			User reporter, ReportTargetType targetType, Long targetId,
			ReportReason reason, String detail
	) {
		return Report.builder()
				.reporter(reporter)
				.targetType(targetType)
				.targetId(targetId)
				.reason(reason)
				.detail(detail)
				.build();
	}

}
