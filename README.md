# bukang

러닝 대회 참가권·굿즈 마켓플레이스 '크루런'의 모노레포입니다.

## 폴더 구조

| 폴더 | 내용 | 안내 |
|---|---|---|
| `backend/` | Spring Boot 4.1 / Java 25 | [backend/README.md](backend/README.md) |
| `frontend/` | Next.js 16 (App Router) | [frontend/AGENTS.md](frontend/AGENTS.md) |
| `docs/` | 기획(`ideation/`), 정책(`policy/`), 작업 관리(`LINEAR.md`) | |
| `.claude/` | 공용 Claude Code 설정, 컨벤션(`rules/`), 스킬 | |

빌드·실행 명령은 각 폴더 안에서 실행합니다 (`cd backend && ./gradlew build`, `cd frontend && pnpm dev`).

## 개발 환경 설정

프로젝트를 받은 뒤(저장소 clone 또는 압축 파일) 각자 컴퓨터에서 한 번씩 해야 하는 설정입니다.
로그인 정보와 토큰은 프로젝트에 들어 있지 않으므로, 모든 팀원이 직접 설정해야 합니다.
Windows와 macOS에서 모두 동작하며, 명령이 다른 부분은 따로 적었습니다.

압축 파일로 받았다면 `.git` 폴더가 없으므로 아래의 git 관련 단계(2단계, 3단계의 `git update-index`)는 건너뜁니다.
macOS에서 `backend/gradlew` 실행 시 `Permission denied`가 나면 `chmod +x backend/gradlew`를 한 번 실행합니다.

### 1. 필요한 도구

- JDK 25 (Gradle toolchain이 사용)
- Docker Desktop (MySQL, Kafka, Elasticsearch, Redis 컨테이너 실행)
- Node.js 20.9 이상 + pnpm (프론트엔드, MCP 서버를 `npx`로 실행)
- Claude Code

### 2. git 사용자 정보

커밋 작성자로 쓰입니다. 자기 이름과 이메일로 설정합니다.

```bash
git config --global user.name "이름"
git config --global user.email "이메일@example.com"
```

커밋 메시지 검사(commitlint)와 커밋 전 포맷 검사(lint-staged)는 `frontend/`의 husky가 저장소 전체에 겁니다.
husky는 `pnpm install` 때 설치되므로, **백엔드만 작업하더라도** clone 후 한 번 실행합니다.

```bash
cd frontend && pnpm install
```

### 3. 환경변수

`.mcp.json`에는 `${변수:-기본값}` 형태의 참조만 있고 실제 값은 없습니다. MCP 서버를 쓰려면 아래 값을 채워야 합니다.
`.env` 파일의 값은 `.mcp.json`에 적용되지 않으므로, 아래 두 방법 중 하나로 설정합니다.

| 변수 | 용도 | 필수 | 로컬 compose 기준 값 |
|---|---|---|---|
| `GITHUB_PERSONAL_ACCESS_TOKEN` | GitHub MCP (이슈, PR 조회) | GitHub MCP를 쓸 때 | 각자 발급한 토큰 |
| `CONTEXT7_API_KEY` | Context7 MCP (라이브러리 문서 조회) | 선택 (없으면 제한된 호출량으로 동작) | 각자 발급한 키 |
| `MYSQL_HOST`, `MYSQL_PORT` | MySQL MCP (읽기 전용 조회) | MySQL MCP를 쓸 때 | `127.0.0.1`, `3306` (기본값과 같음) |
| `MYSQL_USER`, `MYSQL_PASS` | 〃 | 〃 | `bukang2`, `bukang2` |
| `MYSQL_DB` | 〃 | 〃 | `bukang-db` |

`.mcp.json`의 MySQL 기본값(`root`, 비밀번호 없음, `bukang`)은 지금 `compose.yml`의 DB와 다릅니다.
설정하지 않으면 MySQL MCP가 연결에 실패하니, 위 표의 값으로 설정합니다.

#### 방법 1. 환경변수로 설정 (권장)

`.mcp.json`을 수정하지 않아도 되고, 토큰이 파일에 남지 않습니다.

**Windows**: PowerShell에서 아래처럼 설정한 뒤 **터미널과 IDE를 다시 시작**합니다.

```powershell
setx GITHUB_PERSONAL_ACCESS_TOKEN "ghp_..."
setx MYSQL_USER "bukang2"
setx MYSQL_PASS "bukang2"
setx MYSQL_DB "bukang-db"
```

**macOS**: `~/.zshrc`에 아래 내용을 추가한 뒤 **새 터미널을 열고, 그 터미널에서 Claude Code를 실행**합니다.

```bash
export GITHUB_PERSONAL_ACCESS_TOKEN="ghp_..."
export MYSQL_USER="bukang2"
export MYSQL_PASS="bukang2"
export MYSQL_DB="bukang-db"
```

#### 방법 2. `.mcp.json`의 값을 직접 바꾸기

`${...}` 부분을 실제 값으로 바꿉니다.

```json
"MYSQL_USER": "bukang2",
"MYSQL_PASS": "bukang2",
"MYSQL_DB": "bukang-db",
```

`.mcp.json`은 저장소에 커밋된 공유 파일입니다. 이렇게 바꾼 내용은 **커밋하지 않습니다.**
특히 GitHub 토큰 같은 개인 키는 이 방법으로 넣지 말고 방법 1을 사용합니다.
git 저장소로 받았다면, 실수로 커밋되지 않게 아래 명령으로 로컬 변경을 git이 무시하도록 할 수 있습니다.

```bash
git update-index --skip-worktree .mcp.json      # 로컬 변경 무시
git update-index --no-skip-worktree .mcp.json   # 다시 추적 (원격의 .mcp.json 변경을 받을 때)
```

### 4. MCP 서버

`.claude/settings.json`에 `enableAllProjectMcpServers: true`가 있어서, 프로젝트 루트에서 Claude Code를 실행하면
`.mcp.json`의 MCP 서버가 따로 묻지 않고 모두 켜집니다. 서버는 처음 실행할 때 `npx`로 내려받습니다.

| 서버 | 용도 |
|---|---|
| `context7` | 라이브러리 최신 문서 조회 |
| `github` | GitHub 이슈, PR 조회 |
| `playwright` | 브라우저 자동화 |
| `sequential-thinking` | 단계별 사고 보조 |
| `shrimp-task-manager` | 작업 계획, 관리 |
| `mysql` | MySQL 읽기 전용 조회 (로컬 MySQL이 실행 중이어야 함) |
| `linear` | Linear 이슈, 프로젝트 조회 |

### 5. Linear 로그인

Linear MCP는 OAuth로 인증합니다.

1. Claude Code에서 `/mcp`를 실행합니다.
2. `linear`를 선택하고 브라우저에서 Linear 계정으로 로그인합니다.
3. `programmers-05` 워크스페이스에 초대된 계정이어야 PRO 팀 이슈를 볼 수 있습니다.

### 6. 확인

`/mcp`에서 각 서버가 connected 상태인지 확인합니다. 실패한 서버가 있으면 해당 환경변수나 로컬 서비스(MySQL 등)를 확인합니다.

