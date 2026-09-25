-- 이메일 인증번호·토큰을 Redis 로 옮긴다 (DEC-009).
--
-- TTL 이 있는 휘발 데이터라 refresh token(DEC-010) 과 같은 기준을 적용한다.
-- 만료는 Redis TTL 이 처리하므로 정리 배치가 필요 없고, 1회용은 GETDEL 이
-- 원자적으로 보장하므로 consumed_at 플래그도 필요 없다.
--
-- 어떤 FK 도 이 테이블을 참조하지 않아 제거가 고립돼 있다.
DROP TABLE IF EXISTS `email_verification`;
