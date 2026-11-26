---
name: feature-run
description: 새 화면, 새 API, 티켓 여러 개로 나눌 만한 기능을 요청받으면 쓴다. 인터뷰로 설계를 정하고 스펙 이슈와 티켓 이슈를 만든다. 문구·색·간격 수정, 버그 수정, 파일 하나 안의 작은 변경에는 쓰지 않고 바로 고친다.
argument-hint: "[이슈 번호]"
---

# 기능 설계: 인터뷰 → 스펙 → 티켓

기능 하나를 구현할 수 있는 티켓 이슈들로 나눕니다. 코드는 쓰지 않습니다. 구현은 `/ticket-run`이 합니다.

## 시작 전 확인

1. 아래 명령으로 맷 포콕 플러그인 위치를 찾습니다. 단계마다 이 방식으로 `<name>`만 바꿔 찾습니다.

   ```bash
   ls -d ~/.claude/plugins/cache/*/mattpocock-skills/*/skills/engineering/grill-with-docs/SKILL.md | sort -V | tail -1
   ```

2. 아무것도 나오지 않으면 멈춥니다. 사용자에게 `claude plugin install mattpocock-skills@claude-plugins-official`을 실행하고 Claude를 다시 시작하라고 안내합니다.
3. 인자로 이슈 번호를 받았으면 `gh issue view <번호> --json number,title,body,labels,comments`로 읽고 설계의 출발점으로 씁니다. 인자가 없으면 대화에서 받은 요청이 출발점입니다.

## 절차

아래 세 파일을 차례로 Read하고, 각 파일의 절차를 그대로 따릅니다. 내용을 이 파일에 옮겨 적지 않습니다.

1. `grill-with-docs`: 사용자에게 질문해 설계를 정합니다.
2. `to-spec`: 정한 내용을 스펙 이슈로 만듭니다.
3. `to-tickets`: 스펙을 티켓 이슈로 나눕니다. 티켓은 `.github/ISSUE_TEMPLATE/ticket.yml` 칸을 따르고 스펙 이슈의 하위 이슈로 만듭니다.

레포 문서(`GLOSSARY.md`, `docs/agents/*.md`)가 이슈 위치와 라벨 이름을 정합니다. 맷 포콕 스킬이 라벨 역할 이름을 말하면 `docs/agents/triage-labels.md` 표로 바꿉니다.

## 끝내기

1. 티켓마다 라벨 하나를 붙입니다. 붙인 라벨은 `gh issue edit <번호> --add-label "<라벨>"`로 확인합니다.
   - `팩토리 구현`: 사람 없이 구현할 수 있다. 티켓에 작업 내용, 테스트 항목, 테스트 방법이 모두 있고 판단이 남아 있지 않다.
   - `구현 대기`: 사람이 판단하거나, 외부 시스템에 접근하거나, 직접 눌러 확인해야 한다.
2. 아래 형식으로 끝맺습니다.
   - 만든 스펙 이슈와 티켓 번호 목록, 티켓마다 붙인 라벨
   - 다음 할 일: 번호를 정해 `/ticket-run <티켓 번호>`로 사람과 함께 구현하거나, 번호 없이 `/ticket-run`으로 `팩토리 구현` 티켓을 순서대로 구현합니다.
