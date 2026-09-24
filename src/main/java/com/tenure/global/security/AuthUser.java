package com.tenure.global.security;

/**
 * 인증된 요청의 주체. 컨트롤러는 {@code @AuthenticationPrincipal AuthUser user} 로 받는다.
 *
 * <p>DB 를 치지 않는다 — 이 서비스에는 권한 등급이 없고 요청마다 필요한 것은 사용자 id 뿐이라
 * access token 클레임만으로 주체가 완성된다. 사용자 정보가 필요하면 Service 가 조회한다.
 */
public record AuthUser(Long id) {
}
