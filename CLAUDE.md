# CLAUDE.md

러닝 대회 참가권·굿즈 마켓플레이스 '크루런'의 모노레포입니다.

## 폴더

- `backend/`: Spring Boot 백엔드. 작업 전에 `backend/CLAUDE.md`를 읽습니다. Gradle 명령은 `backend/`에서 실행합니다.
- `frontend/`: Next.js 프론트엔드. 작업 전에 `frontend/AGENTS.md`를 읽습니다. pnpm 명령은 `frontend/`에서 실행합니다.
- `docs/`: 기획(`ideation/`)과 정책(`policy/`). 백엔드·프론트가 함께 보는 원본입니다.
- `.claude/rules/`: 공용 컨벤션 (커밋은 `git-commit-convention.md`, 나머지는 백엔드용).

## 공통 규칙

- 커밋은 Conventional Commits를 따릅니다. 프론트의 husky가 commitlint로 저장소 전체 커밋을 검사합니다.
- Linear 이슈를 만들거나 고칠 때, 이슈 작업을 시작할 때, PR을 열 때는 `docs/LINEAR.md`를 먼저 읽고 따릅니다.
- 코드·문서를 고치는 작업을 시작하면 첫 수정 전에 `docs/LINEAR.md`의 "작업 시작 체크"로 Linear 이슈 상태를 맞춥니다.

## 팀 맥락

모든 팀원의 Claude가 같은 용어와 정책을 읽도록 레포 안의 글을 원본으로 둡니다.

@CONTEXT.md

- 정책 질문은 `docs/policy/`를 먼저 찾습니다. 코드 주석의 「정책 N장 M-K」는 `docs/policy/NN-*.md`의 해당 항을 가리킵니다. 표기 규칙은 `docs/policy/README.md`에 있습니다.
- 문서에 없거나 본문이 `(회의 후 채움)`이면 지어내지 않고 "정해지지 않음"이라고 답합니다. 그리고 `docs/policy/questions.md`에 남기자고 제안합니다.
- 자료를 쓸 때는 상태값 영어나 코드 이름 대신 뜻으로 씁니다. "→ 그래서 무슨 일이 생기는지"까지 씁니다. 비유와 번역체는 쓰지 않습니다.
- 정책 질문에는 `policy-question` 스킬을 씁니다.
- 용어집에 없는 단어나 새 용어를 만나면 `CONTEXT.md`의 「용어 후보」에 넣자고 제안합니다. Claude가 `CONTEXT.md` 본문을 직접 고치지 않습니다. 용어는 회의에서 정한 뒤 반영합니다.
- 팀원에게 보낼 자료는 `handoff` 스킬(전달문 정리)로 세 줄 머리말(작업 요청·요청 사유·참고 자료)을 붙여 만듭니다. 빈 양식은 `docs/templates/handoff.md`입니다.
