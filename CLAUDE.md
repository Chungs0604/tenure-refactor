# Tenure

OOTD 착용 기록을 남기고, 판매 시 그 기록으로 옷의 가치를 증명하는 패션 C2C 거래 서비스.
Spring Boot 4.1.1 / Java 21 / Gradle

## DB

MySQL 8. JPA 규칙은 db-mysql 스킬을 따른다.

- 문자셋 `utf8mb4` / collation `utf8mb4_0900_ai_ci`
- 기본 collation이 **대소문자를 무시**한다. `user.email`·`user.nickname`의 UNIQUE가
  `Sujun`/`sujun`을 같은 값으로 보므로 별도 정규화가 필요 없다
- 테이블명은 단수형 snake_case. `order`는 예약어라 거래 테이블은 `trade_order`

## 로컬 실행 준비 (IMPORTANT)

비밀값은 `.env` 로 분리했다. `application.yaml` 에 기본값이 없으므로 **없으면 기동하지 않는다.**

```
cp .env.example .env    # DB_PASSWORD · JWT_SECRET · REDIS_PASSWORD · MAIL_PASSWORD · HOLIDAY_API_SERVICE_KEY
```

`HOLIDAY_API_SERVICE_KEY` — 공공데이터포털 "한국천문연구원_특일 정보" 서비스 키. order 도메인의
정산 영업일 계산(`global/holiday/*`)에 쓴다. data.go.kr 에서 개발계정으로 활용신청하면 받는다
(승인까지 시간이 걸릴 수 있다). `_type=json` 쿼리 파라미터로 JSON 응답을 받는다 — 신청 화면의
"데이터포맷: XML" 은 그 파라미터를 안 줬을 때의 기본값일 뿐이다.

Spring Boot 는 `.env` 를 자동으로 읽지 않는다. 둘 중 하나로 주입한다.

- IntelliJ — 실행 구성의 Environment variables 에 넣거나 EnvFile 플러그인으로 `.env` 지정
- 셸 — `set -a; . ./.env; set +a` 후 실행

호스트·포트·DB명 같은 **비밀이 아닌 설정은 `application.yaml` 에 로컬 기본값**이 있어 건드릴 필요 없다.

## 명령 (IMPORTANT)

```
빌드     ./gradlew build
테스트   ./gradlew test
단일     ./gradlew test --tests 'com.tenure.<클래스명>'
```

코드를 변경한 뒤에는 반드시 테스트를 실행하고 결과를 보고할 것.

## 브랜치

`feat/<개발 API명>`. 전역 규약의 작업 흐름(브랜치 → PR → `develop` 병합)을 그대로 따르되
이름만 이 형태로 쓴다.

```
feat/login        feat/signup        feat/post
```

`develop` 에서 따고 PR base 도 `develop` 이다. 저장소 기본 브랜치는 `main` 이라
PR 을 열 때 base 를 명시해야 한다 — `gh pr create --base develop`.

API ID(`API-001` 등)는 브랜치명이 아니라 **PR 본문의 `변경된 API`** 에 적는다.
`docs/api-spec.md` 와 대조하기 위해서다.

## 구조

```
domain/<도메인>/  controller, dto(request/response), entity, exception, mapper, repository, service
global/          config, exception, response
```

베이스 패키지: `com.tenure`

## 참조 문서

경로만 적는다. 전부 크기가 커서 자동으로 읽어들이지 않고, 필요할 때 해당 파일을 연다.

- 데이터 모델 정본: `docs/schema.yaml` — 엔티티·컬럼·제약·설계 결정(DEC-001 등)
- 사람이 읽는 뷰: `docs/erd.md` — schema.yaml 에서 생성된다
- API 명세: `docs/api-spec.md` — API ID 와 엔드포인트
- 설계 감사 경위: `docs/design-audit.md`

## 이 프로젝트 고유 결정

- 아이템은 소유자가 바뀌어도 행을 유지한다. 소유 구간은 `item_ownership`에 쌓고
  착용 기록(`item_wear_record`)은 아이템에 영구 귀속한다. 복제하면 기록 승계가 깨진다
- 결제가 주문보다 먼저 생성된다. 제안 시 승인(`authorized_at`) → 판매자 수락 시 확정(`captured_at`)
- 금액·배송지·정산계좌는 거래 시점 스냅샷이다. 원본이 바뀌어도 거래는 불변
- 이미지 컬럼 `*_image_key` 에는 **저장소 키**를 넣는다(전체 URL 아님). `app.storage.type`(local|s3)으로
  구현이 갈리고 `FileStorage.toUrl()` 이 base-url 을 붙인다
