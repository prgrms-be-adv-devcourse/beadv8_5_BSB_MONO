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
pnpm test:e2e       # Playwright (e2e/). 가짜 응답 모드 서버를 직접 띄운다. E2E_BASE_URL을 주면 그 서버를 테스트
                    # 처음에는 pnpm exec playwright install chromium 으로 브라우저를 받는다
                    # pnpm dev가 이미 떠 있어 서버 시작에서 멈추면 E2E_BASE_URL=http://localhost:3000 으로 그 서버를 쓴다
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
  - 백엔드에 없는 가짜 응답 모드 전용 API는 보충 파일의 `paths`에 적는다. 지금은 체험용 PASS 본인인증(`/api/v1/auth/identity-verifications/pass`) 하나다.
  - 게이트웨이가 넣는 `X-Member-Id` 헤더는 요청 함수 인자에서 뺀다 (`orval.config.ts`).
- 가입 본인인증은 포트원 V2 + KG이니시스 통합인증이다. `features/identity-verification`의 `requestIdentityVerification()` 하나가 백엔드에서 인증 ID 받기 → 포트원 창 열기 → 백엔드 확인을 한다.
  가입 요청에는 이름 · 생년월일 · 휴대폰 번호 대신 `verificationToken`을 보내고, 백엔드가 포트원에서 확인한 값으로 회원을 만든다.
  가짜 응답 모드에서는 포트원 창 대신 체험용 창(`MockPassSheet`)이 열리고 MSW가 답한다(`shared/mocks/identity-verification.ts`).
  모바일은 포트원 창이 페이지를 옮겨 가므로, 창을 열기 전에 약관 동의와 이메일 · 닉네임을 sessionStorage에 남기고 돌아오면 되살린다(`views/auth/model/signup-draft.ts`).
- 가져올 때는 `@/shared/api`의 서비스별 묶음을 쓴다: `productApi`(요청 함수·훅), `productModel`(타입). zod 스키마 `productZod`는 `@/shared/api-zod`에서 가져온다. `@/shared/api`는 모든 화면이 받으므로 zod를 넣지 않는다.
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

## 코드 작성 규칙

새 코드를 쓰거나 고칠 때 아래 8개 영역을 모두 지킨다. 고친 파일 안에서 규칙에 어긋난 곳을 보면 같은 PR에서 맞춘다.

### 1. 컴포넌트

- 컴포넌트는 순수하게 만든다. 같은 props면 같은 화면을 그리고, 렌더 중에 바깥 값을 바꾸지 않는다.
  현재 시각·난수·`window`는 렌더에서 읽지 않는다. 서버와 브라우저 화면이 달라져 hydration 오류가 난다. 현재 시각은 `@/shared/lib`의 `useNow()`로 마운트 뒤에 정한다.
- 계산·변환 로직은 컴포넌트 밖 순수 함수로 빼서 슬라이스의 `lib/`에 두고, 옆에 `*.test.ts`를 둔다.
- props를 두 단계 넘게 내려야 하면 먼저 `children`으로 조립하고, 그래도 안 되면 `createStoreContext`로 나눈다.
- 폼은 react-hook-form + zod로 만든다(입력값을 ref로 들고 있어 글자마다 다시 그리지 않는다). 가능하면 orval이 만든 zod 스키마를 쓴다.
- 목록의 `key`는 데이터의 id다.
- `'use client'`는 클릭·입력이 있는 말단 컴포넌트에만 붙인다. `page.tsx`와 views의 맨 위 컴포넌트는 서버 컴포넌트로 둔다.
- 라우트 그룹마다 `error.tsx`를 두고(`widgets/status-screens`의 `RouteErrorScreen`), 느린 영역만 `Suspense`·`loading.tsx`로 따로 감싼다.
- 폴더는 위 「폴더 구조」의 FSD를 따른다.

### 2. 상태

- 서버 데이터는 TanStack Query, UI 상태(모달, 단계별 입력)는 Zustand에 둔다.
- 스토어는 `shared/lib`의 `createStoreContext`로 요청별로 만든다. 모듈 전역 스토어는 서버에서 사용자끼리 섞인다.
- `persist`를 쓰면 `partialize`로 꼭 필요한 값만 저장하고, 저장값은 마운트 뒤에 읽는다(`skipHydration`).
- 자주 바뀌는 값은 Context가 아니라 Zustand selector로 나눠 읽는다. Context 값이 바뀌면 그 아래가 모두 다시 그려진다.
- 다른 값에서 계산할 수 있는 값은 state로 저장하지 않고 렌더에서 계산한다.
- 필터·정렬·페이지·탭은 URL `searchParams`에 둔다. 새로고침·공유·뒤로 가기에서 그대로 남는다.

### 3. 훅·비동기

- `useEffect`는 바깥 시스템(구독, 타이머, DOM, 브라우저 API)과 맞출 때만 쓴다. 사용자 행동에 대한 처리는 이벤트 핸들러에 쓴다. effect마다 cleanup을 확인한다.
- 커스텀 훅은 `use`로 시작하고, 한 가지 일만 하며, 슬라이스의 `model/`에 둔다.
- 서버 호출은 TanStack Query로 한다(늦게 온 응답 무시·취소·재시도를 맡는다). 직접 `fetch`할 때는 `AbortController`로 이전 요청을 취소하고, 로딩·오류 상태를 화면에 그린다.
- 메모는 React Compiler(`next.config.ts`의 `reactCompiler`)가 맡는다. 새 코드는 `useMemo`·`useCallback`·`memo` 없이 쓰고, 프로파일러로 느린 곳을 확인했을 때만 넣는다.

### 4. 렌더링·데이터 불러오기

- 로그인 없이 보는 첫 화면 데이터는 `page.tsx`에서 `prefetchQuery` + `HydrationBoundary`로 미리 받는다. 예시는 `app/(shop)/races/page.tsx`.
- 쿼리는 엔티티의 `queryOptions` 함수나 orval이 만든 훅으로만 부른다. 같은 queryKey에는 언제나 같은 모양의 데이터가 들어가야 한다. 모양이 다르면 다른 화면이 캐시를 읽다가 깨진다.
- 변경 뒤에는 바뀐 리소스의 queryKey만 invalidate한다.
- `staleTime` 기본값은 `shared/api/query-client.ts`에 있다. 화면에서 바꾸면 이유를 한 줄 주석으로 남긴다.
- Next 캐시(`revalidate`, `revalidateTag`, `revalidatePath`)를 쓰면 태그 이름을 상수로 한곳에 모으고, 데이터를 바꾸는 곳에서 같은 태그를 재검증한다.
- Server Action을 만들면 액션 안에서 zod로 입력을 검증하고 권한을 확인한다. 화면에서 막았다고 서버에서 빼지 않는다.

### 5. 성능·메모리

- 이미지는 `next/image`, 글꼴은 `next/font`(`app/layout.tsx`)로 쓴다.
- 무거운 컴포넌트(달력, 차트, 에디터)는 `next/dynamic`이나 별도 진입점으로 뺀다. 날짜 고르기(`DatePicker`, `DateTimeField`)는 그래서 `@/shared/ui`가 아니라 `@/shared/date-picker`에서 가져온다.
- 의존성을 새로 넣기 전에 번들에 얼마나 실리는지 확인한다. 몇 줄로 되는 일에는 의존성을 넣지 않는다.
- 긴 목록은 페이지·더 보기로 나눠 받는다. 한 화면에 수백 개를 그려야 하면 가상 목록을 쓴다.
- 이벤트 리스너·타이머·Observer·구독은 cleanup에서 해제한다.
- 첫 화면 구조를 크게 바꾼 PR은 Lighthouse로 LCP·INP·CLS를 재서 「확인 방법」에 적는다.

### 6. SEO·접근성

- 로그인 없이 보는 화면은 `metadata`·`generateMetadata`로 제목·설명·Open Graph·canonical을 채우고, 새 공개 경로를 `app/sitemap.ts`에 더한다.
- 대회 상세처럼 검색에 노출할 화면은 JSON-LD 구조화 데이터를 넣는다.
- 제목은 `h1`부터 순서대로 쓰고, 동작은 `button`, 이동은 `a`(`Link`)로 쓴다.
- 이미지는 `alt`, 글자 없는 아이콘 버튼은 `aria-label`을 단다. 모든 동작을 키보드로 할 수 있게 하고, 글자 대비는 4.5:1 이상으로 한다.

### 7. 스타일

- className 합치기는 `@/shared/lib`의 `cn()`(clsx + tailwind-merge) 하나로 한다.
- 색·간격·둥글기는 디자인 토큰(`app/tokens.css`, `pnpm tokens`로 생성)만 쓴다. 토큰에 없는 값이 필요하면 피그마 변수에 먼저 더한다.
- 한 컴포넌트의 모양 변형(크기·종류·상태)은 cva로 정의한다.
- 다크 모드는 `.dark` 클래스로만 켠다(`app/globals.css`). 반응형은 Tailwind 기본 breakpoint만 쓴다.

### 8. 코드 품질·보안

- 타입을 모르면 `any` 대신 `unknown`으로 받고 zod로 좁힌다. API 응답 밖에서 오는 값(URL, storage, `postMessage`)은 zod로 검증한다.
- 사용자가 쓴 글(커뮤니티 글, 문의)은 텍스트로만 그린다. `dangerouslySetInnerHTML`은 쓰지 않는다.
- 비밀 값에는 `NEXT_PUBLIC_`을 붙이지 않는다. 토큰 저장 위치는 위 「API」의 인증 항목을 따른다.
- 쓰지 않게 된 코드·파일·의존성은 그 변경을 한 PR에서 지운다.
- 순수 함수·훅은 Vitest 단위 테스트를 옆에 둔다. 주요 사용자 흐름은 `e2e/`의 Playwright 테스트로 확인하고, 테스트 이름은 피그마 「Test Scenario」 페이지 번호를 따른다(예: `시나리오 22 · 개인 일반 예매`).
- 실행 중 오류는 서버는 `instrumentation.ts`의 `onRequestError`, 브라우저는 `error.tsx`의 GA4 `app_error` 이벤트로 모은다.

## 환경변수

서버 값은 `shared/config/env.ts`에서 zod로, 브라우저 값(`NEXT_PUBLIC_*`)은 `shared/config/client-env.ts`에서 zod 없이 검증한다. 새 변수는 둘 중 맞는 곳과 `.env.example`에 같이 추가한다.
브라우저 값은 `@/shared/config`의 `clientEnv`, 서버 값은 `@/shared/server-env`의 `getServerEnv()`로 가져온다. 나눈 이유는 브라우저 묶음에 zod가 실리지 않게 하려는 것이다.
`API_MOCKING`, `IMAGE_REMOTE_HOSTS`, `NEXT_PUBLIC_*`는 next.config에서 쓰여 **빌드 시점에 고정**된다. 바꾸면 다시 빌드해야 한다.

## 배포

- 임시: Vercel (Root Directory = 이 폴더). 백엔드가 없으면 `API_MOCKING=on`.
- 최종: AWS. `Dockerfile`(standalone)로 이미지를 만들고 런타임에 `BACKEND_URL`을 백엔드 내부 주소로 준다.
- 이미지: 기본은 Next 최적화 + `IMAGE_REMOTE_HOSTS`(S3). `NEXT_PUBLIC_IMAGE_CDN_URL`을 주면 `shared/lib/image/cdn-loader.ts`로 바뀐다.

## 인증

- 실제 API 모드에서 `src/proxy.ts`는 로그인 여부를 검사하지 않는다. Refresh Token 쿠키가 `Path=/api/v1/auth`라 화면 요청에 실리지 않기 때문이다.
  가짜 응답 모드에서는 가짜 로그인이 심는 표시 쿠키(`mockLoggedIn`, `Path=/`)로 회원 화면(`isProtectedPath`)을 서버에서 먼저 `/login?next=`로 보낸다.
- 실제 API 모드에서 로그인 전 진입은 아래처럼 처리한다.
  - 회원 화면(`isProtectedPath`)은 `ProtectedScreenGuard`가 로그인 확인이 끝날 때까지 로딩만 그리고, 비로그인이면 `/login?next=`로 보낸다.
  - `AuthSessionRestorer`는 쓰는 중에 재발급이 실패해 로그인이 끊겼을 때만 `/login`으로 보낸다. 앱을 처음 열 때 재발급이 실패하면 보내지 않는다.
  - `/seller`·`/admin`은 로그인 화면으로 보내지 않는다. 화면마다 그 자리에서 로그인이 필요하다는 안내를 보여 준다.
- 로그아웃이 성공하면 `useLogout`이 문서를 새로 불러 `/login`으로 간다. 토큰을 먼저 비우면 지금 화면이 로그아웃 상태로 잠깐 다시 그려지기 때문이다.
- 소셜 로그인(카카오 · 구글)은 백엔드가 인가 요청부터 콜백까지 처리한다(BE-031). 프론트는 `features/auth`의 `SocialLoginButtons`로 시작 주소(`/api/v1/auth/oauth2/authorize/{provider}`)를 화면 이동으로 연다.
  콜백 뒤 `/auth/social/complete`(재발급으로 Access Token을 받는다), `/signup/social`(소셜 가입), `/login?error=` 중 하나로 온다. 계정 연결은 `/me/accounts`에서 연결 표를 받은 뒤 같은 시작 주소로 연다.

## 커밋

Conventional Commits (`feat:`, `fix:` …). husky가 lint-staged와 commitlint를 돌린다.

## 작업 관리

Linear 이슈를 만들거나 고칠 때, 이슈 작업을 시작할 때, PR을 열 때는 `../docs/LINEAR.md`를 먼저 읽고 따른다.
코드·문서를 고치는 작업을 시작하면 첫 수정 전에 `../docs/LINEAR.md`의 "작업 시작 체크"로 Linear 이슈 상태를 맞춘다.
