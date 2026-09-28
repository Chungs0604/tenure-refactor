-- 소셜 로그인(구글·네이버)을 받을 자리를 만든다 (DEC-017).
--
-- 로그인 화면(2791:15250)의 '다음' 구분선 아래에 구글·네이버 아이콘이 있는데
-- 스키마·명세·제외 근거 어디에도 없었다. 텍스트 덤프가 아이콘을 잡지 못해 생긴 누락이다.
--
-- 소셜 가입은 비밀번호가 없으므로 password_hash 를 nullable 로 바꾼다.
-- 기존 행은 전부 이메일 가입이라 provider 를 LOCAL 로 채운 뒤 NOT NULL 을 건다.
--
-- uk_user_provider 는 같은 소셜 계정으로 두 번 가입하는 것을 막는다.
-- LOCAL 은 provider_id 가 NULL 이고 유니크 인덱스가 NULL 중복을 허용하므로 제약에 걸리지 않는다.

ALTER TABLE `user`
  MODIFY COLUMN `password_hash` VARCHAR(255) NULL COMMENT '비밀번호 해시. 소셜 가입은 NULL (DEC-017)',
  ADD COLUMN `provider` VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT '계정 출처(UserProvider). 이메일 가입은 LOCAL' AFTER `password_hash`,
  ADD COLUMN `provider_id` VARCHAR(100) NULL COMMENT '소셜 제공자의 고유 식별자. LOCAL 이면 NULL' AFTER `provider`;

ALTER TABLE `user`
  ADD UNIQUE KEY `uk_user_provider` (`provider`, `provider_id`);
