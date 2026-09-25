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
 *
 * <p>생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. {@code @Builder} 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
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

}
