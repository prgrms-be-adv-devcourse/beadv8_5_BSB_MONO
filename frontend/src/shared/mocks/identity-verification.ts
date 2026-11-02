import type {
  PassVerificationRequestDto,
  VerifiedIdentityDto,
} from '@/shared/api/generated/member/member.schemas'

/**
 * 가짜 응답 모드(API_MOCKING=on)의 체험용 PASS 본인인증 규칙. 실제 백엔드 모드는 포트원 창과 백엔드가 확인한다.
 *
 * 서버 메모리에 상태를 두지 않는다(Vercel 서버 함수는 요청마다 인스턴스가 다를 수 있다).
 * 인증 토큰에 확인한 정보와 끝 시각을 담아, 가입 목이 받은 토큰만 보고 판단한다.
 */

/** 인증 토큰을 가입 요청에 쓸 수 있는 시간(초). 백엔드와 같은 15분이다 */
const TOKEN_TTL_SECONDS = 15 * 60

const TOKEN_PREFIX = 'mock-verification.'
const PHONE_PATTERN = /^01[016789]-?\d{3,4}-?\d{4}$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const CARRIERS = ['SKT', 'KT', 'LGU', 'MVNO']

/** 목업 응답. MSW 핸들러가 HttpResponse로 바꾼다 */
export type MockAnswer<T> = {
  status: number
  body: { status: number; message: string; data: T | null; code?: string }
}

/** 인증 토큰에 담긴 값. 가입 목이 이 값으로 회원 정보를 만든다 */
export type MockVerificationClaims = {
  name: string
  birthDate: string
  phone: string
  expiresAt: number
}

/** 체험용 PASS 본인인증. 실제 연동이 없으므로 체험용 창에 넣은 값을 그대로 확인된 정보로 돌려준다 */
export function answerMockPass(
  body: Partial<PassVerificationRequestDto> | Record<string, unknown>,
  now = Date.now(),
): MockAnswer<VerifiedIdentityDto> {
  const name = typeof body.name === 'string' ? body.name.trim() : ''
  const phone = typeof body.phone === 'string' ? body.phone.trim() : ''
  const birthDate = typeof body.birthDate === 'string' ? body.birthDate.trim() : ''
  const valid =
    CARRIERS.includes(String(body.carrier)) &&
    name.length > 0 &&
    PHONE_PATTERN.test(phone) &&
    DATE_PATTERN.test(birthDate)
  if (!valid) {
    return {
      status: 400,
      body: {
        status: 400,
        code: 'INVALID_REQUEST',
        message: '본인인증 정보를 확인해주세요.',
        data: null,
      },
    }
  }
  const claims: MockVerificationClaims = {
    name,
    birthDate,
    phone,
    expiresAt: now + TOKEN_TTL_SECONDS * 1000,
  }
  return {
    status: 200,
    body: {
      status: 200,
      message: '본인인증을 확인했습니다.',
      data: {
        verificationToken: TOKEN_PREFIX + encodeURIComponent(JSON.stringify(claims)),
        name,
        birthDate,
        phone,
      },
    },
  }
}

/** 가입 요청의 인증 토큰을 읽는다. 토큰이 없거나 모양이 틀렸거나 15분이 지났으면 null */
export function readMockVerificationToken(
  token: unknown,
  now = Date.now(),
): MockVerificationClaims | null {
  if (typeof token !== 'string' || !token.startsWith(TOKEN_PREFIX)) return null
  try {
    const claims = JSON.parse(
      decodeURIComponent(token.slice(TOKEN_PREFIX.length)),
    ) as MockVerificationClaims
    return claims.expiresAt > now ? claims : null
  } catch {
    return null
  }
}
