# CLAUDE.md

러닝 대회 참가권·굿즈 마켓플레이스 '크루런'의 모노레포입니다.

## 폴더

- `backend/`: Spring Boot 백엔드. 작업 전에 `backend/CLAUDE.md`를 읽습니다. Gradle 명령은 `backend/`에서 실행합니다.
- `frontend/`: Next.js 프론트엔드. 작업 전에 `frontend/AGENTS.md`를 읽습니다. pnpm 명령은 `frontend/`에서 실행합니다.
- `docs/`: 기획(`ideation/`)과 정책(`policy/`). 백엔드·프론트가 함께 보는 원본입니다. 어떤 문서가 어디 있는지는 `docs/README.md`(문서 지도)에서 찾습니다.
- `.claude/rules/`: 공용 컨벤션 (커밋은 `git-commit-convention.md`, 나머지는 백엔드용).

## 공통 규칙

- 커밋은 Conventional Commits를 따릅니다. 프론트의 husky가 commitlint로 저장소 전체 커밋을 검사합니다.
- 이슈는 GitHub Issues에서 다룹니다(`docs/agents/issue-tracker.md`). 이슈를 만들 때는 `.github/ISSUE_TEMPLATE/` 양식을 씁니다.
- 브랜치 이름은 `<종류>/<이슈 번호>-<영어 설명>`이고, 커밋·PR 제목의 영역(scope)은 `.claude/rules/git-commit-convention.md`의 18개 중에서 고릅니다.
- PR 본문은 `.github/pull_request_template.md` 칸을 따릅니다.
- 아직 열려 있는 Linear(BUK·CRU) 이슈를 마무리할 때만 `docs/LINEAR.md`를 읽고, 첫 수정 전에 "작업 시작 체크"로 이슈 상태를 맞춥니다.

## 기능 요청 흐름

- 새 화면, 새 API, 티켓 여러 개로 나눌 만한 기능 요청은 `feature-run` 스킬로 설계부터 시작합니다. 스펙 이슈와 티켓 이슈가 만들어집니다.
- 문구·색·간격 수정, 버그 수정, 파일 하나 안의 작은 변경은 `feature-run` 없이 바로 고칩니다.
- 티켓 구현은 `/ticket-run <티켓 번호>`로 시작합니다. 번호 없이 `/ticket-run`을 부르면 `팩토리 구현` 라벨이 붙은 티켓을 순서대로 구현합니다.
- 월요일 아침에는 `/triage`로 들어온 이슈를 분류합니다.
- 두 스킬은 맷 포콕 플러그인을 읽습니다. 플러그인이 없으면 `claude plugin install mattpocock-skills@claude-plugins-official`을 실행합니다.

## 팀 맥락

모든 팀원의 Claude가 같은 용어와 정책을 읽도록 레포 안의 글을 원본으로 둡니다.

@GLOSSARY.md

- 정책 질문은 `docs/policy/`를 먼저 찾습니다. 코드 주석의 「정책 N장 M-K」는 `docs/policy/NN-*.md`의 해당 항을 가리킵니다. 표기 규칙은 `docs/policy/README.md`에 있습니다.
- 문서에 없거나 본문이 `(회의 후 채움)`이면 지어내지 않고 "정해지지 않음"이라고 답합니다. 그리고 `docs/policy/questions.md`에 남기자고 제안합니다.
- 자료를 쓸 때는 상태값 영어나 코드 이름 대신 뜻으로 씁니다. "→ 그래서 무슨 일이 생기는지"까지 씁니다. 비유와 번역체는 쓰지 않습니다.
- 정책 질문에는 `policy-question` 스킬을 씁니다.
- 용어가 정해지면 Claude가 `GLOSSARY.md` 본문을 바로 수정합니다. 아직 정해지지 않은 단어는 「용어 후보」 표에 적습니다.
- 팀원에게 보낼 자료는 `handoff` 스킬(전달문 정리)로 세 줄 머리말(작업 요청·요청 사유·참고 자료)을 붙여 만듭니다. 빈 양식은 `docs/templates/handoff.md`입니다.

## Agent skills

### Issue tracker

이슈와 스펙은 이 레포의 GitHub Issues에 둔다(`gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

분류 상태 라벨 다섯 개(검토 대기, 정보 필요, 팩토리 구현, 구현 대기, 취소)를 맷 포콕 역할 이름에 연결한다. See `docs/agents/triage-labels.md`.

### Domain docs

single-context: 루트 `GLOSSARY.md`와 `docs/adr/`. See `docs/agents/domain.md`.
