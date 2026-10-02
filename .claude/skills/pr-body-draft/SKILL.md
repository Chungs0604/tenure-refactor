---
name: pr-body-draft
description: |
  현재 브랜치의 변경 사항으로 PR 본문 초안을 만든다. 변경된 컨트롤러의 엔드포인트를
  docs/api-spec.md와 대조해 "변경된 API" 섹션을 채우고, 커밋 로그로 "작업 내용"을 채운다.
  PR을 열기 직전, 본문 초안이 필요할 때 사용한다.
disable-model-invocation: true
---

# PR 본문 초안

`develop` 대비 현재 브랜치 diff로 `.github/pull_request_template.md` 형식의 PR 본문을 채운다.
이 저장소는 PR 본문의 "변경된 API"를 `docs/api-spec.md`와 대조하는 것이 규칙이므로
(프로젝트 CLAUDE.md), 사람이 손으로 API ID를 찾아 적지 않도록 자동화한다.

## 절차

1. **베이스 확인** — `git merge-base HEAD develop`으로 분기점을 찾는다.
   `develop`이 로컬에 없으면 `git fetch origin develop`을 먼저 시도한다.

2. **변경된 컨트롤러 찾기** — `git diff --name-only <분기점>...HEAD`에서
   `*Controller.java` 경로만 추린다. 컨트롤러가 없으면 "변경된 API" 섹션은
   빈 채로 두고 3번을 건너뛴다.

3. **엔드포인트 추출** — 각 컨트롤러에서 클래스 레벨 `@RequestMapping`과
   메서드 레벨 `@GetMapping`/`@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping`을
   읽어 `METHOD /전체경로`를 만든다. diff에 걸린 메서드(추가·수정된 매핑)만 대상으로 한다 —
   파일은 바뀌었지만 매핑 자체는 그대로인 메서드까지 넣지 않는다.

4. **API-spec 대조** — `docs/api-spec.md`의 엔드포인트 표에서 같은 METHOD+path를 찾아
   API ID를 매칭한다. 실제 표는 `| API-001 | \`POST\` | \`/api/v1/auth/login\` | ... |`처럼
   METHOD와 path가 백틱으로 감싸져 있다 — 백틱을 벗기고 비교한다. 매칭이 안 되면
   "API-spec에 없음 — 신규 추정"이라고 표시한다 (조용히 빠뜨리지 않는다 — api-spec.md가
   먼저 갱신돼야 할 수도 있다는 신호다).

5. **작업 내용** — `git log <분기점>..HEAD --oneline`으로 커밋 메시지를 모아
   타입 접두사(`feat:`/`fix:`/`refactor:`/...)별로 묶어 bullet로 정리한다.

6. **본문 조립** — `.github/pull_request_template.md`의 섹션 구조를 그대로 쓰고
   "작업 내용"과 "변경된 API"만 채운다. "관련 이슈"·"테스트 결과"·"확인 필요 사항"·
   체크리스트는 빈 채로 둔다 — 실행 결과나 이슈 번호는 사람이 확인해야 한다.

7. **출력** — 완성된 본문을 그대로 보여준다. PR을 실제로 열지는 사용자에게 먼저 물어본다 —
   연다면 `gh pr create --base develop --title "<커밋 요약>" --body "$(cat <<'EOF' ... EOF)"`.

## 하지 않는 것

- 테스트를 실행하거나 결과를 채워 넣지 않는다 — 실행 안 한 걸 했다고 쓰지 않는다
- api-spec.md를 대신 고치지 않는다. 매칭 안 되는 API가 있으면 사람에게 알린다
- 사용자 확인 없이 `gh pr create`를 실행하지 않는다
