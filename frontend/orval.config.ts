import { defineConfig } from 'orval'

/**
 * 백엔드(springdoc) OpenAPI 명세로 API 코드를 생성한다. 아직 명세가 없어서 실행하지 않는다.
 *
 *   OPENAPI_URL=http://localhost:8080/v3/api-docs pnpm api:generate
 *
 * 결과는 src/shared/api/generated/ 에 태그별로 나뉜다.
 * - <tag>.ts       : 요청 함수 + TanStack Query 훅
 * - <tag>.msw.ts   : MSW 핸들러 (src/shared/mocks/handlers.ts에 등록)
 * - <tag>.zod.ts   : 폼 검증에 쓸 zod 스키마
 */
const input = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs'

export default defineConfig({
  api: {
    input,
    output: {
      mode: 'tags-split',
      target: 'src/shared/api/generated',
      schemas: 'src/shared/api/generated/model',
      client: 'react-query',
      httpClient: 'fetch',
      mock: true,
      clean: true,
      override: {
        mutator: { path: 'src/shared/api/fetcher.ts', name: 'customFetch' },
        fetch: { includeHttpResponseReturnType: false },
      },
    },
  },
  apiZod: {
    input,
    output: {
      mode: 'tags-split',
      target: 'src/shared/api/generated',
      client: 'zod',
      fileExtension: '.zod.ts',
    },
  },
})
