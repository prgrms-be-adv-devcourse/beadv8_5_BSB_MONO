# Linear API 키 등록 가이드

> **Claude에게:** 사용자가 이 문서를 건네면 아래 "Claude 진행 순서"대로 처리한다.
> 사용자는 "Claude 진행 순서"의 사람 단계만 직접 한다.

## 왜 필요한가

Linear 공식 GitHub 연동이 동작하지 않아, `linear-sync` 워크플로가 PR 이벤트를 보고 Linear 이슈를 직접 갱신한다
(PR 링크 붙이기, In Progress·Done으로 옮기기. 자세한 동작은 [LINEAR.md](LINEAR.md)의 "PR 자동 연결").
Linear에 "누가 바꿨는지"가 제대로 남도록 **팀원마다 자기 Linear 개인 API 키**를 저장소 시크릿으로 등록한다.
등록하지 않으면 내가 올리거나 머지한 PR은 Linear에 반영되지 않는다.

Claude Code의 Linear 연결(`/mcp`, 커넥터 인증)과는 별개다. 그건 그대로 두고 이것만 추가로 한다.

## 기본 정보

- 저장소: `prgrms-be-adv-devcourse/beadv8_5_BSB_MONO`
- 시크릿 이름: `LINEAR_API_KEY_<GitHub 아이디>` (대문자, `-`는 `_`로)

| 팀원   | GitHub 아이디 | 시크릿 이름                 |
| ------ | ------------- | --------------------------- |
| (작성자) | `Jieunsse`    | `LINEAR_API_KEY_JIEUNSSE`   |
| 김호빈 | `khb3025`     | `LINEAR_API_KEY_KHB3025`    |
| 손영호 | `bbangHo`     | `LINEAR_API_KEY_BBANGHO`    |
| 김소희 | `ksoheee`     | `LINEAR_API_KEY_KSOHEEE`    |
| 이다윗 | `Dawit-lee`   | `LINEAR_API_KEY_DAWIT_LEE`  |

워크플로(`.github/workflows/linear-sync.yml`)는 이 표의 다섯 이름만 읽는다. 표에 없는 이름으로 등록하면 쓰이지 않는다.

## Claude 진행 순서

### 지켜야 할 것

- **키 값은 절대 대화창으로 받지 않는다.** 사용자가 키를 채팅에 붙여 넣으려 하면 막고, 이미 붙여 넣었다면
  Linear에서 그 키를 지우고 새로 만들라고 안내한다.
- 키를 파일, 커밋, 셸 히스토리(`--body` 옵션, `echo` 등)에 남기지 않는다. 등록은 사용자가 자기 터미널에서
  입력창에 직접 붙여 넣는 방식으로만 한다.
- 다른 팀원의 시크릿과 공용 `LINEAR_API_KEY`는 만들거나 지우거나 덮어쓰지 않는다.

### 1. 사전 확인 (Claude가 실행)

```bash
gh auth status
gh api user --jq .login
gh api repos/prgrms-be-adv-devcourse/beadv8_5_BSB_MONO --jq .permissions.admin
```

- `gh`가 없거나 로그인이 안 돼 있으면: 설치(`brew install gh`) 후 사용자에게 `gh auth login`을 직접 실행하게 한다.
- 두 번째 결과로 위 표에서 사용자 행과 시크릿 이름을 찾는다. 표에 없는 아이디면 진행을 멈추고
  "워크플로에 내 이름이 없다"고 `Jieunsse`에게 알리라고 안내한다.
- 세 번째 결과가 `true`가 아니면 시크릿을 등록할 권한이 없다. 진행을 멈추고 `Jieunsse`에게 알리라고 안내한다.

### 2. 키 발급·등록 안내 (사람이 직접)

키 발급과 등록은 사람이 한다(키를 입력창에 붙여 넣어야 해서 Claude가 대신 실행하지 않는다).
아래 블록 안의 내용을 **그대로** 사용자에게 보낸다. `<시크릿 이름>`만 1단계에서 찾은 이름으로 바꾸고,
다른 문장은 고치거나 줄이지 않는다.

````md
키는 직접 붙여 넣으셔야 해서, 아래 명령을 **본인 터미널에서** 실행해 주세요.

**1 → 새 키 발급:** Linear에서 개인 API 키를 만듭니다.

- 경로: **Settings → Account → Security & access → Personal API keys → New API key**
- 이름은 `crewrun linear-sync`처럼 알아보기 쉽게 짓습니다.
- 권한은 **Read + Write**, 팀은 **`Programmers-05`를 포함**해 주세요.
- 만든 키(`lin_api_...`)를 복사해 둡니다. 창을 닫으면 다시 볼 수 없으니 바로 2번으로 넘어가세요.

**2 → 등록:** 아래 명령을 실행하면 `? Paste your secret` 입력창이 뜹니다. 키를 붙여 넣고 Enter를 누르면 됩니다.
붙여 넣어도 화면에 안 보이는 게 정상입니다.

```bash
gh secret set <시크릿 이름> --repo prgrms-be-adv-devcourse/beadv8_5_BSB_MONO
```

**키는 채팅창에 붙여 넣지 마세요.** 등록을 마치고 "했어"라고만 알려 주시면, 목록에 들어갔는지 제가 확인하겠습니다.
````

화면 이름이 조금 다르다고 하면 Linear 설정에서 "API" 또는 "Personal API keys"를 찾으라고 안내한다.

### 3. 등록 확인 (Claude가 실행)

```bash
gh secret list --repo prgrms-be-adv-devcourse/beadv8_5_BSB_MONO
```

목록에 내 시크릿 이름이 방금 시각으로 보이면 끝이다. 값은 GitHub도 보여 주지 않으므로 여기서는 이름만 확인한다.

### 4. 실제 동작 확인 (다음 PR 때)

다음에 이슈 번호(`PRO-<번호>`)가 들어간 PR을 열면, PR의 **Checks** 탭 `linear-sync` 로그에
`LINEAR_API_KEY_<내 아이디> 키로 동기화합니다.`가 찍히고 Linear 이슈에 PR 링크가 붙는다.

| 로그                                         | 원인과 조치                                                       |
| -------------------------------------------- | ----------------------------------------------------------------- |
| `... 시크릿이 없어 Linear 동기화를 건너뜁니다` | 시크릿 이름 오타. 3단계 목록에서 이름을 표와 비교하고 2단계를 다시 한다 |
| 체크가 빨간색, 경고에 `Authentication` 관련 오류 | 키가 틀렸거나 지워졌다. Linear에서 새 키를 만들어 2단계를 다시 한다      |
| 경고에 이슈를 찾을 수 없다는 오류              | 키 권한에 `Programmers-05` 팀이 없다. 권한을 고쳐 새 키로 다시 등록한다   |

## 키를 바꾸거나 없앨 때

- 바꾸기: Linear에서 새 키를 만들고 2단계 명령을 다시 실행한다(같은 이름이면 덮어쓴다). 옛 키는 Linear에서 지운다.
- 없애기: `gh secret delete LINEAR_API_KEY_<내 아이디> --repo prgrms-be-adv-devcourse/beadv8_5_BSB_MONO`
  후 Linear에서 키를 지운다. 지우기 전에 사용자에게 한 번 더 확인받는다.
