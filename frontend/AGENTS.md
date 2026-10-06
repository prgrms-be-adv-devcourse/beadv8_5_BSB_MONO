<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# frontend

러닝 대회 참가권·굿즈 마켓플레이스의 프론트엔드. 핵심 차별점은 크루 단체 접수(크루원별 개별 결제).
기획·정책 문서는 저장소 루트 `docs/`(`../docs/`)에 있다. 최종 인프라는 Nginx(HTTP 처리, 라우팅) → Next.js / Spring Boot 백엔드(AWS EC2 Docker Compose). 인증을 한곳에서 처리해야 할 때 별도 API 게이트웨이를 둘 수 있다.

모노레포의 `frontend/` 폴더다. 명령은 이 폴더에서 실행한다. 백엔드는 `../backend/`.

## 명령어

```bash
pnpm dev            # .env.local에 API_MOCKING=on이면 MSW로 동작
pnpm lint           # ESLint (FSD 층 규칙 포함)
pnpm format         # Prettier
pnpm typecheck      # next typegen + tsc
pnpm test           # Vitest
pnpm build
pnpm api:generate   # orval. OPENAPI_URL=<springdoc 주소> 필요
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

## API

- 코드에서는 항상 상대 경로 `/api/...`로 부른다. 모든 호출은 `shared/api`의 `customFetch`를 지난다.
  - 브라우저: 로컬·Vercel은 next.config rewrites → `BACKEND_URL`, AWS는 Nginx → 백엔드
  - 서버 컴포넌트: `BACKEND_URL`을 붙여 직접 호출. `API_MOCKING=on`이면 MSW 핸들러에서 바로 응답
- API 타입·훅은 orval로 생성해 `shared/api/generated/`에 둔다. 생성 파일은 직접 고치지 않는다.
- 생성된 MSW 목은 `shared/mocks/handlers.ts`에 등록한다. 테스트도 같은 핸들러를 쓴다.
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

## 미정 사항

- 인증 방식 (소셜 로그인 예정). `src/proxy.ts`, `shared/api/fetcher.ts`의 `TODO(auth)` 참고.

## 커밋

Conventional Commits (`feat:`, `fix:` …). husky가 lint-staged와 commitlint를 돌린다.

## 작업 관리

Linear 이슈를 만들거나 고칠 때, 이슈 작업을 시작할 때, PR을 열 때는 `../docs/LINEAR.md`를 먼저 읽고 따른다.
