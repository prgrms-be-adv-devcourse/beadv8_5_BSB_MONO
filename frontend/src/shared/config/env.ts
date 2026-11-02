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

const clientSchema = z.object({
  /** 설정하면 next/image가 서버 최적화 대신 이 CDN 주소로 이미지를 요청한다. */
  NEXT_PUBLIC_IMAGE_CDN_URL: optional(z.url()),
  /**
   * 토스페이먼츠 결제창(API 개별 연동) 클라이언트 키. 테스트 키(test_ck_)만 받는다 — 운영 키를 실수로 넣으면 빌드가 멈춘다.
   * 백엔드 cash-service의 TOSS_SECRET_KEY(test_sk_)와 같은 상점의 짝이어야 승인된다. 비우면 충전 버튼이 실패 화면으로 간다.
   */
  NEXT_PUBLIC_TOSS_CLIENT_KEY: optional(
    z.string().startsWith('test_ck_', '토스 클라이언트 키는 테스트 키(test_ck_)만 씁니다.'),
  ),
  /**
   * Google 태그 관리자 컨테이너 ID(GTM-로 시작). 비우면 GTM을 넣지 않고 이벤트도 보내지 않는다.
   * 가짜 응답 모드(API_MOCKING=on)에서도 넣지 않는다. 값은 레포에 쓰지 않고 .env.local · 저장소 Variables에 넣는다.
   */
  NEXT_PUBLIC_GTM_ID: optional(z.string().startsWith('GTM-', 'GTM ID는 GTM-으로 시작합니다.')),
  /** next.config가 API_MOCKING 값을 그대로 넣는다. 브라우저가 가짜 응답 모드인지 알아야 토스 결제창을 건너뛸 수 있다. */
  NEXT_PUBLIC_API_MOCKING: z.enum(['on', 'off']).default('off'),
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
  NEXT_PUBLIC_TOSS_CLIENT_KEY: process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY,
  NEXT_PUBLIC_GTM_ID: process.env.NEXT_PUBLIC_GTM_ID,
  NEXT_PUBLIC_API_MOCKING: process.env.NEXT_PUBLIC_API_MOCKING,
})
