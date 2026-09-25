package com.tenure.domain.user.enums;

/**
 * 회원탈퇴 화면은 있으나 상태값 칩이 화면에 없음. 탈퇴 후에도 소유 이력이 남아야 해 물리 삭제 불가
 */
public enum UserStatus {

	ACTIVE,
	WITHDRAWN;

}
