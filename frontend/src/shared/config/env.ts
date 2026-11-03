import { z } from 'zod'

// 빈 문자열 환경변수(KEY=)는 "값 없음"으로 본다.
const optional = <T extends z.ZodType>(schema: T) =>
  z.preprocess((v) => (v === '' ? undefined : v), schema.optional())

const serverSchema = z.object({
  /** 서버(RSC·rewrites)가 API를 보낼 곳. 백엔드 API 게이트웨이 주소(로컬 8000). AWS에서는 게이트웨이 내부 주소. */
  BACKEND_URL: z.url().default('http://localhost:8000'),
  /** 공유 카드(og:image 등)의 절대 주소를 만들 사이트 주소. 비우면 Vercel 운영 주소 → localhost 순. */
  SITE_URL: optional(z.url()),
  /** on이면 /api 요청을 MSW 핸들러가 응답한다. 빌드 시점에 고정된다. */
  API_MOCKING: z.enum(['on', 'off']).default('off'),
  /** next/image가 최적화를 허용할 이미지 호스트(쉼표 구분). 예: S3 버킷 도메인 */
  IMAGE_REMOTE_HOSTS: z
    .string()
    .default('')
    .transform((s) =>
      s
        .split(',')
        .map((h) => h.trim())
        .filter(Boolean),
    ),
})

export type ServerEnv = z.infer<typeof serverSchema>

let serverEnv: ServerEnv | undefined

/** 서버 전용. 브라우저에서 부르면 에러. */
export function getServerEnv(): ServerEnv {
  if (typeof window !== 'undefined') {
    throw new Error('getServerEnv()는 서버에서만 호출할 수 있습니다.')
  }
  serverEnv ??= serverSchema.parse(process.env)
  return serverEnv
}

// next.config가 이 파일에서 함께 가져간다
export { clientEnv } from './client-env'
