package com.tenure.domain.user.entity;

import com.tenure.domain.user.enums.Gender;
import com.tenure.domain.user.enums.UserProvider;
import com.tenure.domain.user.enums.UserGrade;
import com.tenure.domain.user.enums.UserStatus;
import com.tenure.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 사용자. 계정 정보(실명·휴대폰)와 프로필(닉네임·체형)을 함께 보유한다.
 *
 * 생성 메서드는 기능 구현 시 추가한다 — 무엇이 필수 인자인지는 도메인 규칙이라
 * 스키마에서 읽을 수 없다. @Builder 가 PRIVATE 이라 팩토리 없이는 만들 수 없다.
 */
@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class User extends BaseEntity {

	/** PK 관례 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 회원가입 > 이메일. '이미 존재하는 이메일입니다' / '유효하지 않은 이메일입니다' */
	@Column(name = "email", nullable = false, unique = true, length = 255)
	private String email;

	/** 회원가입 > 비밀번호 '(8자 이상)'. 평문 저장 불가로 해시 컬럼 */
	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	/** 계정 출처. 이메일 가입이면 LOCAL 이다 (DEC-017) */
	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private UserProvider provider;

	/** 소셜 제공자의 고유 식별자. LOCAL 이면 NULL */
	@Column(name = "provider_id", length = 100)
	private String providerId;

	/** 로그인 정보 > 이름 '홍길동' */
	@Column(name = "real_name", nullable = false, length = 50)
	private String realName;

	/** 로그인 정보 > 휴대폰번호 '821012345678' */
	@Column(name = "phone", nullable = false, length = 20)
	private String phone;

	/** 프로필 작성 > 닉네임. '중복된 닉네임입니다' / '닉네임을 설정해주세요.' */
	@Column(name = "nickname", nullable = false, unique = true, length = 30)
	private String nickname;

	/** '프로필 사진은 필수로 받지 않습니다! 등록하지 않았다면 기본 프로필 사진'. 저장소 키를 넣는다 (DEC-006) */
	@Column(name = "profile_image_key", length = 500)
	private String profileImageKey;

	/** 프로필 작성 > 성별 */
	@Enumerated(EnumType.STRING)
	@Column(name = "gender", length = 10)
	private Gender gender;

	/** 프로필 작성 > 키 '170cm'. 검색 필터 '필터/키-몸무게'의 대상 */
	@Column(name = "height_cm")
	private Integer heightCm;

	/** 프로필 작성 > 몸무게 '64kg' */
	@Column(name = "weight_kg")
	private Integer weightKg;

	/** '유저가 레코드 사용자인지, 기본 사용자인지에 따라 수수료 부담·배송비 섹션이 서로 다릅니다' */
	@Enumerated(EnumType.STRING)
	@Column(name = "grade", nullable = false, length = 20)
	private UserGrade grade;

	/** 회원탈퇴 화면 존재 + 탈퇴 후에도 이력 참조 필요 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status;

	/** 탈퇴 시 개인정보 파기 시점. NULL이면 파기 전 (→ DEC-004) */
	@Column(name = "anonymized_at")
	private LocalDateTime anonymizedAt;

	/** 레코드 등급 보장 만료 시각(승격 +30일). 재평가 배치의 대상 선별 기준 */
	@Column(name = "grade_expires_at")
	private LocalDateTime gradeExpiresAt;

	/** 검색 계정 탭·인기 사용자·OOTD 상세 헤더에 '팔로우 101명'이 목록으로 반복 노출된다. 정본은 follow 테이블 */
	@Column(name = "follower_count", nullable = false)
	private Integer followerCount;

	/** 같은 목록에 '게시물 60개'·'피드 60개'·'OOTD 289개'가 함께 노출된다. 정본은 post 테이블 */
	@Column(name = "post_count", nullable = false)
	private Integer postCount;

	/**
	 * 회원가입. 이메일 인증을 통과한 뒤에만 호출된다 (API-012).
	 *
	 * 비밀번호는 해시를 받는다 — 엔티티가 인코더를 알면 도메인이 보안 구현에 묶인다.
	 * 등급은 BASIC 으로 시작하고 레코드 승격은 게시 시점에 판정한다.
	 */
	public static User signUp(
			String email, String passwordHash, String realName,
			String phone, String nickname, String profileImageKey,
			Gender gender, Integer heightCm, Integer weightKg
	) {
		return signUp(email, passwordHash, UserProvider.LOCAL, null, realName,
				phone, nickname, profileImageKey, gender, heightCm, weightKg);
	}

	/**
	 * 소셜 가입까지 받는 전체 팩토리 (DEC-017).
	 *
	 * 소셜 가입은 비밀번호가 없어 passwordHash 가 NULL 이고 제공자 식별자를 갖는다.
	 * 이메일 가입은 그 반대다 — 어느 쪽이든 한쪽은 비어 있다.
	 */
	public static User signUp(
			String email, String passwordHash, UserProvider provider, String providerId,
			String realName, String phone, String nickname, String profileImageKey,
			Gender gender, Integer heightCm, Integer weightKg
	) {
		return User.builder()
				.email(email)
				.passwordHash(passwordHash)
				.provider(provider)
				.providerId(providerId)
				.realName(realName)
				.phone(phone)
				.nickname(nickname)
				.profileImageKey(profileImageKey)
				.gender(gender)
				.heightCm(heightCm)
				.weightKg(weightKg)
				.grade(UserGrade.BASIC)
				.status(UserStatus.ACTIVE)
				.followerCount(0)
				.postCount(0)
				.build();
	}

	/**
	 * 비밀번호 재설정
	 * 해시를 받는다 — 엔티티가 인코더를 알지 않는다.
	 */
	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	/**
	 * 프로필 수정.
	 *
	 * 받은 값으로 그대로 덮어쓴다 — 사진만 null 을 허용하며 그때는 기본 프로필로 돌아간다.
	 * 닉네임 중복은 Service 가 먼저 확인한다.
	 */
	public void updateProfile(
			String nickname, String profileImageKey,
			Gender gender, Integer heightCm, Integer weightKg
	) {
		this.nickname = nickname;
		this.profileImageKey = profileImageKey;
		this.gender = gender;
		this.heightCm = heightCm;
		this.weightKg = weightKg;
	}

	public boolean isWithdrawn() {
		return status == UserStatus.WITHDRAWN;
	}

	/** 게시. 정본은 post 테이블이고 이 값은 목록에서 반복 노출되는 카운터다 (§6-3). */
	public void increasePostCount() {
		this.postCount++;
	}

	/** 게시물 삭제. 0 아래로는 내리지 않는다 — 어긋나면 프로필에 음수가 그대로 뜬다. */
	public void decreasePostCount() {
		this.postCount = Math.max(0, this.postCount - 1);
	}

	/**
	 * 레코드 승격. 판정은 UserGradeService 가 한다 — 조건이 게시물 수라 엔티티가 알 수 없다.
	 *
	 * gradeExpiresAt — 보장 만료 시각(승격 +30일). 이 시점에 재평가해 미달이면 강등한다
	 */
	public void promoteToRecord(LocalDateTime gradeExpiresAt) {
		this.grade = UserGrade.RECORD;
		this.gradeExpiresAt = gradeExpiresAt;
	}

	public boolean isRecordGrade() {
		return grade == UserGrade.RECORD;
	}

}
