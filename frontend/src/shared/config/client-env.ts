// 브라우저용 값은 모든 화면이 받으므로 zod 없이 검사한다(zod는 압축 후 약 90KB). 서버 값은 env.ts가 zod로 검사한다.

type Raw = Record<string, string | undefined>

// 빈 문자열 환경변수(KEY=)는 "값 없음"으로 본다.
const value = (raw: Raw, key: string) => (raw[key] === '' ? undefined : raw[key])

function check(ok: boolean, key: string, message: string) {
  if (!ok) throw new Error(`${key}: ${message}`)
}

function isUrl(text: string) {
  try {
    new URL(text)
    return true
  } catch {
    return false
  }
}

export function parseClientEnv(raw: Raw) {
  /** 설정하면 next/image가 서버 최적화 대신 이 CDN 주소로 이미지를 요청한다. */
  const NEXT_PUBLIC_IMAGE_CDN_URL = value(raw, 'NEXT_PUBLIC_IMAGE_CDN_URL')
  /**
   * 토스페이먼츠 결제창(API 개별 연동) 클라이언트 키. 테스트 키(test_ck_)만 받는다 — 운영 키를 실수로 넣으면 빌드가 멈춘다.
   * 백엔드 cash-service의 TOSS_SECRET_KEY(test_sk_)와 같은 상점의 짝이어야 승인된다. 비우면 충전 버튼이 실패 화면으로 간다.
   */
  const NEXT_PUBLIC_TOSS_CLIENT_KEY = value(raw, 'NEXT_PUBLIC_TOSS_CLIENT_KEY')
  /**
   * Google 태그 관리자 컨테이너 ID(GTM-로 시작). 비우면 GTM을 넣지 않고 이벤트도 보내지 않는다.
   * 가짜 응답 모드(API_MOCKING=on)에서도 넣지 않는다. 값은 레포에 쓰지 않고 .env.local · 저장소 Variables에 넣는다.
   */
  const NEXT_PUBLIC_GTM_ID = value(raw, 'NEXT_PUBLIC_GTM_ID')
  /**
   * Microsoft Clarity(화면 녹화 · 히트맵) 프로젝트 ID. Clarity 프로젝트 Settings → Setup에 있다.
   * 비우거나 가짜 응답 모드(API_MOCKING=on)면 넣지 않는다. 스크립트 주소에 들어가므로 영문 · 숫자만 받는다.
   */
  const NEXT_PUBLIC_CLARITY_ID = value(raw, 'NEXT_PUBLIC_CLARITY_ID')
  /** next.config가 API_MOCKING 값을 그대로 넣는다. 브라우저가 가짜 응답 모드인지 알아야 토스 결제창을 건너뛸 수 있다. */
  const NEXT_PUBLIC_API_MOCKING = value(raw, 'NEXT_PUBLIC_API_MOCKING') ?? 'off'

  if (NEXT_PUBLIC_IMAGE_CDN_URL !== undefined) {
    check(isUrl(NEXT_PUBLIC_IMAGE_CDN_URL), 'NEXT_PUBLIC_IMAGE_CDN_URL', 'URL 형식이어야 합니다.')
  }
  if (NEXT_PUBLIC_TOSS_CLIENT_KEY !== undefined) {
    check(
      NEXT_PUBLIC_TOSS_CLIENT_KEY.startsWith('test_ck_'),
      'NEXT_PUBLIC_TOSS_CLIENT_KEY',
      '토스 클라이언트 키는 테스트 키(test_ck_)만 씁니다.',
    )
  }
  if (NEXT_PUBLIC_GTM_ID !== undefined) {
    check(
      NEXT_PUBLIC_GTM_ID.startsWith('GTM-'),
      'NEXT_PUBLIC_GTM_ID',
      'GTM ID는 GTM-으로 시작합니다.',
    )
  }
  if (NEXT_PUBLIC_CLARITY_ID !== undefined) {
    check(
      /^[a-z0-9]+$/i.test(NEXT_PUBLIC_CLARITY_ID),
      'NEXT_PUBLIC_CLARITY_ID',
      'Clarity ID는 영문 · 숫자만 씁니다.',
    )
  }
  check(
    NEXT_PUBLIC_API_MOCKING === 'on' || NEXT_PUBLIC_API_MOCKING === 'off',
    'NEXT_PUBLIC_API_MOCKING',
    'on 또는 off여야 합니다.',
  )

  return {
    NEXT_PUBLIC_IMAGE_CDN_URL,
    NEXT_PUBLIC_TOSS_CLIENT_KEY,
    NEXT_PUBLIC_GTM_ID,
    NEXT_PUBLIC_CLARITY_ID,
    /** 포트원 상점 아이디(store-로 시작). 가입 본인인증 창을 열 때 쓴다. 비우면 실제 백엔드 모드에서 본인인증 창이 열리지 않는다. */
    NEXT_PUBLIC_PORTONE_STORE_ID: value(raw, 'NEXT_PUBLIC_PORTONE_STORE_ID'),
    /** 포트원 본인인증 채널 키(KG이니시스 통합인증). 상점 아이디와 같은 상점의 채널이어야 한다. */
    NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY: value(
      raw,
      'NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY',
    ),
    /** 구글 Search Console 소유 확인 코드(HTML 태그 방식의 content 값). 비우면 확인 태그를 넣지 않는다. */
    NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION: value(raw, 'NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION'),
    /** 네이버 서치어드바이저 소유 확인 코드(HTML 태그 방식의 content 값). 비우면 확인 태그를 넣지 않는다. */
    NEXT_PUBLIC_NAVER_SITE_VERIFICATION: value(raw, 'NEXT_PUBLIC_NAVER_SITE_VERIFICATION'),
    NEXT_PUBLIC_API_MOCKING: NEXT_PUBLIC_API_MOCKING as 'on' | 'off',
  }
}

// NEXT_PUBLIC_* 값은 빌드 때 문자열로 치환되므로 하나씩 직접 참조해야 한다.
export const clientEnv = parseClientEnv({
  NEXT_PUBLIC_IMAGE_CDN_URL: process.env.NEXT_PUBLIC_IMAGE_CDN_URL,
  NEXT_PUBLIC_TOSS_CLIENT_KEY: process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY,
  NEXT_PUBLIC_GTM_ID: process.env.NEXT_PUBLIC_GTM_ID,
  NEXT_PUBLIC_CLARITY_ID: process.env.NEXT_PUBLIC_CLARITY_ID,
  NEXT_PUBLIC_PORTONE_STORE_ID: process.env.NEXT_PUBLIC_PORTONE_STORE_ID,
  NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY: process.env.NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY,
  NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION: process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION,
  NEXT_PUBLIC_NAVER_SITE_VERIFICATION: process.env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION,
  NEXT_PUBLIC_API_MOCKING: process.env.NEXT_PUBLIC_API_MOCKING,
})
