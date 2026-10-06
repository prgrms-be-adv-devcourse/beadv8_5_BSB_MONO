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
