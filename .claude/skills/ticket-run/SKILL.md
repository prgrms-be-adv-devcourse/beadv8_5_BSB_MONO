---
name: ticket-run
description: 티켓 이슈를 구현해 PR까지 올린다. 번호를 주면 그 티켓 하나를 사람과 함께, 번호 없이 부르면 팩토리 구현 라벨이 붙은 티켓을 막는 순서대로 하나씩 구현한다.
argument-hint: "[티켓 번호]"
disable-model-invocation: true
model: sonnet
effort: medium
---

# 티켓 구현

티켓 하나를 테스트와 함께 구현하고 PR로 올립니다. 이 파일이 구현 절차의 유일한 원본입니다. GitHub Actions(`.github/workflows/ticket-run.yml`)도 이 파일을 읽습니다.

## 시작 전 확인

1. 맷 포콕 플러그인 위치를 찾습니다. 단계마다 `<name>`만 바꿔 찾습니다.

   ```bash
   ls -d ~/.claude/plugins/cache/*/mattpocock-skills/*/skills/engineering/tdd/SKILL.md | sort -V | tail -1
   ```

2. 아무것도 나오지 않으면 멈춥니다. 사용자에게 `claude plugin install mattpocock-skills@claude-plugins-official`을 실행하고 Claude를 다시 시작하라고 안내합니다.
3. `gh auth status`가 실패하면 멈추고 `gh auth login`을 안내합니다.

## 번호 있음: 티켓 하나, 이 세션에서 사람과 함께

1. 담당자를 확인합니다. `gh issue view <번호> --json assignees,state`에서 이미 닫혔거나 담당자가 있으면 사용자에게 알리고 멈춥니다.
2. 가져갑니다: `gh issue edit <번호> --add-assignee @me`
3. 아래 「구현 절차」를 이 세션에서 따릅니다. 테스트할 경계는 사용자에게 확인받습니다.

## 번호 없음: 팩토리 구현 티켓을 하나씩

1. 대상을 찾습니다: `gh issue list --state open --label "팩토리 구현" --json number,title,assignees`. 담당자가 있는 티켓은 뺍니다.
2. 막는 순서를 확인합니다. 티켓의 blocked_by 이슈가 모두 닫혀 있어야 시작할 수 있습니다. 확인 방법은 `docs/agents/issue-tracker.md`의 Blocking 항목을 따릅니다. 시작할 수 있는 티켓이 없으면 기다리는 티켓과 그 이유를 보고하고 끝냅니다.
3. 티켓마다 하위 에이전트를 열기 전에 `gh issue edit <번호> --add-assignee @me`를 실행합니다. 담당자가 이미 있으면 그 티켓은 건너뜁니다.
4. 그다음 새 하위 에이전트를 model `sonnet`, effort `medium`으로 열고, 그 에이전트가 아래 「구현 절차」를 따릅니다. 하위 에이전트에는 티켓 번호와 이 파일의 경로만 줍니다. 두 티켓을 동시에 돌리지 않습니다. 하위 에이전트가 끝나면 다음 티켓으로 갑니다.

## 구현 절차

1. 티켓을 읽습니다: `gh issue view <번호> --json number,title,body,labels,comments`. 상위 스펙 이슈도 읽습니다.
2. `GLOSSARY.md`와 `CODING_STANDARDS.md`를 읽습니다. 관련 `docs/policy/`, `docs/adr/`가 있으면 읽습니다.
3. 테스트할 경계를 정합니다.
   - 번호 있음: 경계 목록을 만들어 사용자에게 확인받습니다. 경계마다 잡는 것과 놓치는 것을 한 줄씩 적습니다.
   - 번호 없음: 티켓의 「테스트 항목」을 경계로 씁니다. 테스트 항목이 없으면 라벨을 `구현 대기`로 바꾸고(`gh issue edit <번호> --remove-label "팩토리 구현" --add-label "구현 대기"`) 이유를 이슈 댓글로 남기고 다음 티켓으로 갑니다.
4. 맷 포콕 `tdd`를 Read하고 그 방식으로 구현합니다. 테스트 하나를 쓰고, 실패하는 것을 확인하고, 통과할 만큼만 구현합니다. 이 순서를 반복합니다.
   - 사이클마다 「테스트 이름 → 실패 출력 한 줄 → 통과 확인」을 기록해 둡니다. PR 검증 칸에 씁니다.
   - 테스트는 레포 기존 방식(백엔드 JUnit, 프론트 Vitest)으로 씁니다. 금지하는 테스트는 `CODING_STANDARDS.md`를 따릅니다.
   - 통과한 테스트를 구현에 맞추려고 고치지 않습니다. 테스트가 틀려 보이면 멈추고 보고합니다. 번호 없음이면 이슈에 이유를 댓글로 남기고, 라벨을 `구현 대기`로 바꾸고, 다음 티켓으로 갑니다.
5. 바꾼 모듈의 전체 테스트를 돌립니다. 출력이 통과여야 다음으로 갑니다.
   - 백엔드: `backend/`에서 `./gradlew :<모듈>:test` 와 `./gradlew :<모듈>:checkstyleMain :<모듈>:checkstyleTest`
   - 프론트: `frontend/`에서 `pnpm lint && pnpm typecheck && pnpm test`
6. 맷 포콕 `code-review`를 Read하고 두 축으로 리뷰합니다. 기준 축에는 `CODING_STANDARDS.md`를 줍니다. 지적은 같은 브랜치에서 고치고 5번을 다시 돌립니다.
7. 브랜치 이름은 `<종류>/<이슈 번호>-<영어 설명>`입니다. 예: `feat/829-ticket-run`. 구현을 시작하기 전에 `gh issue develop <번호> --name <브랜치> --base dev --checkout`으로 브랜치를 만듭니다. 이렇게 만들어야 이슈의 Development 칸에 브랜치가 연결됩니다. 커밋하고 push합니다. 종류는 `.claude/rules/git-commit-convention.md`의 타입이고, 커밋 제목의 영역은 같은 파일의 목록에서 고릅니다.
8. PR을 만듭니다: base `dev`, `--assignee @me`, 본문은 `.github/pull_request_template.md` 칸을 모두 채우고 마지막 줄에 `Closes #<번호>`. 제목은 `<종류>(<영역>): <한글 설명>`입니다.
9. 맷 포콕 `retro`를 Read하고 이 세션에 대해 돌립니다. 환경을 고칠 제안이 나오면 PR이 아니라 보고에 적습니다.

## 보고

티켓 번호별로 아래를 적습니다.

- PR 주소, 또는 멈춘 이유와 바꾼 라벨
- 사이클별 실패 → 통과 기록
- 돌린 명령과 결과
- retro가 낸 제안
