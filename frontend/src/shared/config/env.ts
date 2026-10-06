import { z } from 'zod'

// 빈 문자열 환경변수(KEY=)는 "값 없음"으로 본다.
const optional = <T extends z.ZodType>(schema: T) =>
  z.preprocess((v) => (v === '' ? undefined : v), schema.optional())

const serverSchema = z.object({
  /** 서버(RSC·rewrites)가 API를 보낼 곳. AWS에서는 백엔드 내부 주소. */
  BACKEND_URL: z.url().default('http://localhost:8080'),
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

const clientSchema = z.object({
  /** 설정하면 next/image가 서버 최적화 대신 이 CDN 주소로 이미지를 요청한다. */
  NEXT_PUBLIC_IMAGE_CDN_URL: optional(z.url()),
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

// NEXT_PUBLIC_* 값은 빌드 때 문자열로 치환되므로 하나씩 직접 참조해야 한다.
export const clientEnv = clientSchema.parse({
  NEXT_PUBLIC_IMAGE_CDN_URL: process.env.NEXT_PUBLIC_IMAGE_CDN_URL,
})
