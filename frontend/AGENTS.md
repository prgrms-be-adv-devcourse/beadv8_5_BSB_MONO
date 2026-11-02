<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# frontend

러닝 대회 참가권·굿즈 마켓플레이스의 프론트엔드. 핵심 차별점은 크루 단체 접수(크루원별 개별 결제).
기획·정책 문서는 저장소 루트 `docs/`(`../docs/`)에 있다. 최종 인프라는 Nginx(HTTP 처리, 라우팅) → Next.js / 백엔드 API 게이트웨이(Spring Cloud Gateway, 8000) → 서비스별 Spring Boot 앱(AWS EC2 Docker Compose). Nginx 설정과 compose는 `../backend/`(`docker/nginx/default.conf`, `compose.yml`)에 있다.

모노레포의 `frontend/` 폴더다. 명령은 이 폴더에서 실행한다. 백엔드는 `../backend/`.

## 명령어

```bash
pnpm dev            # .env.local에 API_MOCKING=on이면 MSW로 동작
pnpm lint           # ESLint (FSD 층 규칙 포함)
pnpm format         # Prettier
pnpm typecheck      # next typegen + tsc
pnpm test           # Vitest
pnpm storybook      # 시안 컴포넌트 확인 (localhost:6006). 스토리는 컴포넌트 옆 *.stories.tsx
pnpm build
pnpm api:generate   # orval. openapi/<서비스>.json → src/shared/api/generated/<서비스>/
pnpm api:pull       # 로컬에서 띄운 서비스(8080~8085)의 /v3/api-docs를 openapi/에 받기
```

작업을 끝냈다고 말하기 전에 `pnpm lint && pnpm typecheck && pnpm test`를 돌린다.

## 폴더 구조 (얇은 FSD)

```
src/
  app/        Next 라우팅 + FSD app 층. page.tsx는 views를 불러오기만 한다.
    (shop) (seller) (admin) (auth)   라우트 그룹. _providers/는 전역 Provider
    api/[[...path]]/                 API_MOCKING=on일 때 MSW 응답
  proxy.ts    (Next 16의 middleware) /seller, /admin 접근 확인 자리
  views/      화면 조립 (FSD pages 층. Next pages/와 겹쳐서 이름을 바꿈)
  widgets/    여러 기능을 묶은 큰 블록 (헤더, 크루 결제 현황판 등)
  features/   사용자 행동 단위 (장바구니 담기, 크루 초대 수락 등)
  entities/   도메인 객체 (race, crew, order 등)
  shared/     api, config, lib, mocks, ui 세그먼트
```

- import는 위 층 → 아래 층으로만. 같은 층의 다른 슬라이스는 import 금지.
- 다른 슬라이스/세그먼트는 `index.ts`(public API)로만 가져온다. ESLint(eslint-plugin-boundaries)가 막는다.
- 폴더는 필요할 때 만든다. 빈 층에 미리 슬라이스를 만들지 않는다.
- 슬라이스 안 세그먼트: `ui/`, `model/`(상태·훅), `api/`, `lib/`.

## 레이아웃

- 회원 화면 기둥 폭은 600px(`--layout-app-max-width`). 값을 직접 쓰지 말고 `max-w-(--layout-app-max-width)`처럼 토큰을 쓴다.
- 마이 · 알림 · 내 참가권의 본문 위 여백은 `pt-(--layout-content-start)`(화면 높이의 10%, 16~96px). 가운데 정렬 대신 시작 위치만 고정한다.

## API

- 코드에서는 항상 상대 경로 `/api/...`로 부른다. 모든 호출은 `shared/api`의 `customFetch`를 지난다.
  - 브라우저: 로컬·Vercel은 next.config rewrites → `BACKEND_URL`(게이트웨이, 로컬 8000), AWS는 Nginx → 게이트웨이
  - 서버 컴포넌트: `BACKEND_URL`을 붙여 직접 호출. `API_MOCKING=on`이면 MSW 핸들러에서 바로 응답
- API 타입·훅은 orval로 생성해 `shared/api/generated/<서비스>/`에 둔다. 생성 파일은 직접 고치지 않는다.
  - 명세는 서비스별로 `openapi/<서비스>.json`에 커밋해 둔다 (member, cash, payout, file, order, product).
  - 성공 응답 스키마가 명세에 없는 API는 `openapi/supplements/<서비스>.json`으로 보충한다. 백엔드가 스키마를 내면 지운다.
  - 게이트웨이가 넣는 `X-Member-Id` 헤더는 요청 함수 인자에서 뺀다 (`orval.config.ts`).
- 가져올 때는 `@/shared/api`의 서비스별 묶음을 쓴다: `productApi`(요청 함수·훅), `productModel`(타입), `productZod`.
- 응답은 `RsData{status, message, data, code?}`로 감싸여 온다. `customFetch`는 본문을 그대로 돌려주고, data는 `unwrapData`로 꺼낸다(`select: unwrapData`).
  2xx가 아니면 `ApiError`(`serverMessage`, `code`)를 던진다.
- 인증: Access Token은 브라우저 메모리(`shared/api/access-token.ts`)에만 두고 `customFetch`가 `Authorization: Bearer`로 붙인다.
  Refresh Token은 서버가 내려 주는 httpOnly 쿠키(`Path=/api/v1/auth`)라 프론트가 만지지 않는다.
  - 401이면 재발급(`POST /api/v1/auth/reissue`)을 한 번만(single-flight) 보내고 원래 요청을 한 번 다시 보낸다. 재발급도 실패하면 로그아웃 상태.
  - 새로고침하면 토큰이 사라지므로 `features/auth`의 `AuthSessionRestorer`(app Provider)가 앱 시작 때 재발급으로 되살린다.
  - 서버 컴포넌트는 토큰을 붙일 수 없다. 로그인이 필요한 데이터는 클라이언트에서 불러온다.
  - 로그인 상태는 `useAuthStatus()`(`pending`·`authenticated`·`anonymous`), 로그인·로그아웃은 `useLogin`·`useLogout`.
- 대회 등록·주문 생성·장바구니 결제·예치금 환급은 `{ idempotencyKey }`를 요청 옵션으로 넘겨 `Idempotency-Key` 헤더를 붙인다(본문에 넣지 않는다).
  키는 `useIdempotencyKey()`로 고른다: 같은 입력으로 다시 보내면(두 번 클릭·재시도) 같은 키, 입력이 바뀌면 새 키. MSW 목도 이 헤더가 없으면 400이다.
- 생성된 MSW 목(명세의 응답 예시)은 `shared/mocks/handlers.ts`에 등록한다. 테스트도 같은 핸들러를 쓴다.
  - 인증 목은 로그인 상태를 쿠키로 흉내 낸다: 가짜 로그인이 `mockRefreshToken` 쿠키(`HttpOnly; SameSite=Lax; Path=/api/v1/auth`, Secure 없음. `shared/mocks/mock-session.ts`)를 심고, 로그아웃이 `Max-Age=0`으로 지운다. 재발급은 요청 쿠키가 있을 때만 성공, 없으면 401. 서버 메모리에 상태를 두지 않는다. Node의 MSW는 쿠키를 저장하지 않으므로 테스트에서 로그인된 채로 요청하려면 `Cookie: MOCK_LOGGED_IN_COOKIE`(`@/shared/testing`)를 직접 붙인다.
- 서버 데이터는 TanStack Query(초기 데이터는 RSC). Zustand에 서버 데이터를 넣지 않는다.

## 상태·폼

- Zustand는 UI 상태(모달, 단계별 입력)만. 모듈 전역 스토어 금지 — `shared/lib`의 `createStoreContext`로 요청별 스토어를 만든다.
- 폼은 react-hook-form + zod. 가능하면 orval이 만든 zod 스키마를 쓴다.

## 환경변수

`shared/config/env.ts`에서 zod로 검증한다. 새 변수는 여기와 `.env.example`에 같이 추가한다.
`API_MOCKING`, `IMAGE_REMOTE_HOSTS`, `NEXT_PUBLIC_*`는 next.config에서 쓰여 **빌드 시점에 고정**된다. 바꾸면 다시 빌드해야 한다.

## 배포

- 임시: Vercel (Root Directory = 이 폴더). 백엔드가 없으면 `API_MOCKING=on`.
- 최종: AWS. `Dockerfile`(standalone)로 이미지를 만들고 런타임에 `BACKEND_URL`을 백엔드 내부 주소로 준다.
- 이미지: 기본은 Next 최적화 + `IMAGE_REMOTE_HOSTS`(S3). `NEXT_PUBLIC_IMAGE_CDN_URL`을 주면 `shared/lib/image/cdn-loader.ts`로 바뀐다.

## 인증

- `src/proxy.ts`는 로그인 여부를 검사하지 않는다. Refresh Token 쿠키가 `Path=/api/v1/auth`라 `/seller`·`/admin` 요청에 실리지 않기 때문이다. 로그인 전 진입은 `AuthSessionRestorer`가 재발급 실패 시 `/login`으로 보낸다.
- 소셜 로그인(카카오 · 구글)은 백엔드가 인가 요청부터 콜백까지 처리한다(BE-031). 프론트는 `features/auth`의 `SocialLoginButtons`로 시작 주소(`/api/v1/auth/oauth2/authorize/{provider}`)를 화면 이동으로 연다.
  콜백 뒤 `/auth/social/complete`(재발급으로 Access Token을 받는다), `/signup/social`(소셜 가입), `/login?error=` 중 하나로 온다. 계정 연결은 `/me/accounts`에서 연결 표를 받은 뒤 같은 시작 주소로 연다.

## 커밋

Conventional Commits (`feat:`, `fix:` …). husky가 lint-staged와 commitlint를 돌린다.

## 작업 관리

Linear 이슈를 만들거나 고칠 때, 이슈 작업을 시작할 때, PR을 열 때는 `../docs/LINEAR.md`를 먼저 읽고 따른다.
코드·문서를 고치는 작업을 시작하면 첫 수정 전에 `../docs/LINEAR.md`의 "작업 시작 체크"로 Linear 이슈 상태를 맞춘다.
