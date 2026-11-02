import type {
  IdentityVerificationDto,
  PassVerificationRequestDto,
  PhoneVerificationDto,
} from './generated/member/member.schemas'

/**
 * 휴대폰 인증 · PASS 본인인증 목업의 규칙. 사업자가 없어 실제 문자 · PASS 연동을 할 수 없어서 만든 가짜 응답이다.
 * 가짜 응답 모드(API_MOCKING=on)에서는 MSW 핸들러가, 실제 백엔드 모드에서는 브라우저(features/identity-verification)가
 * 같은 규칙으로 답한다. 백엔드에는 이 API가 없다.
 *
 * 서버 메모리에 상태를 두지 않는다(Vercel 서버 함수는 요청마다 인스턴스가 다를 수 있다).
 * 인증 번호표(verificationId)와 인증 토큰에 휴대폰 번호와 끝 시각을 담아, 받은 값만 보고 판단한다.
 */

/** 문자를 보내지 않으므로 인증번호는 늘 이 값이다. 화면에도 이 값을 알려 준다. */
export const MOCK_VERIFICATION_CODE = '123456'
/** 인증번호를 쓸 수 있는 시간(초) */
export const MOCK_CODE_TTL_SECONDS = 180
/** 인증 토큰을 가입 요청에 쓸 수 있는 시간(초) */
const TOKEN_TTL_SECONDS = 30 * 60

const ID_PREFIX = 'mock-phone.'
const TOKEN_PREFIX = 'mock-verification.'
const PHONE_PATTERN = /^01[016789]-?\d{3,4}-?\d{4}$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const CARRIERS = ['SKT', 'KT', 'LGU', 'MVNO']

/** 목업 응답. MSW는 HttpResponse로, 브라우저는 성공이면 body를, 실패면 ApiError로 바꾼다. */
export type MockAnswer<T> = {
  status: number
  body: { status: number; message: string; data: T | null; code?: string }
}

/** 인증 토큰에 담긴 값. 가입 목이 요청의 휴대폰 번호와 맞는지 볼 때 쓴다. */
export type MockVerificationClaims = {
  method: 'PHONE' | 'PASS'
  phone: string
  name: string | null
  birthDate: string | null
  expiresAt: number
}

const digits = (phone: string) => phone.replace(/\D/g, '')

function encode(value: unknown): string {
  return encodeURIComponent(JSON.stringify(value))
}

function decode<T>(value: string, prefix: string): T | null {
  if (!value.startsWith(prefix)) return null
  try {
    return JSON.parse(decodeURIComponent(value.slice(prefix.length))) as T
  } catch {
    return null
  }
}

function fail(status: number, code: string, message: string): MockAnswer<never> {
  return { status, body: { status, code, message, data: null } }
}

function issueToken(claims: Omit<MockVerificationClaims, 'expiresAt'>, now: number) {
  const full: MockVerificationClaims = { ...claims, expiresAt: now + TOKEN_TTL_SECONDS * 1000 }
  const data: IdentityVerificationDto = {
    verificationToken: TOKEN_PREFIX + encode(full),
    method: claims.method,
    phone: claims.phone,
    name: claims.name,
    birthDate: claims.birthDate,
  }
  return data
}

/** 인증번호 받기. 번호 형식만 보고 언제나 보낸 것으로 답한다. */
export function answerPhoneVerificationRequest(
  body: { phone?: unknown },
  now = Date.now(),
): MockAnswer<PhoneVerificationDto> {
  const phone = typeof body.phone === 'string' ? body.phone.trim() : ''
  if (!PHONE_PATTERN.test(phone)) {
    return fail(400, 'INVALID_REQUEST', '휴대폰 번호 형식에 맞게 작성해주세요.')
  }
  const verificationId =
    ID_PREFIX + encode({ phone, expiresAt: now + MOCK_CODE_TTL_SECONDS * 1000 })
  return {
    status: 201,
    body: {
      status: 201,
      message: '인증번호를 보냈습니다.',
      data: { verificationId, expiresInSeconds: MOCK_CODE_TTL_SECONDS },
    },
  }
}

/** 인증번호 확인. 시간이 지났으면 410, 번호가 틀리면 400, 맞으면 인증 토큰을 준다. */
export function answerPhoneVerificationConfirm(
  verificationId: string,
  body: { code?: unknown },
  now = Date.now(),
): MockAnswer<IdentityVerificationDto> {
  const saved = decode<{ phone: string; expiresAt: number }>(verificationId, ID_PREFIX)
  if (!saved || typeof saved.phone !== 'string') {
    return fail(400, 'INVALID_REQUEST', '인증번호를 다시 받아주세요.')
  }
  if (saved.expiresAt <= now) {
    return fail(
      410,
      'AUTH_VERIFICATION_EXPIRED',
      '인증 시간이 지났습니다. 인증번호를 다시 받아주세요.',
    )
  }
  if (body.code !== MOCK_VERIFICATION_CODE) {
    return fail(400, 'AUTH_VERIFICATION_CODE_MISMATCH', '인증번호가 맞지 않습니다.')
  }
  return {
    status: 200,
    body: {
      status: 200,
      message: '본인 확인을 마쳤습니다.',
      data: issueToken({ method: 'PHONE', phone: saved.phone, name: null, birthDate: null }, now),
    },
  }
}

/** PASS 본인인증. 실제 연동이 없으므로 PASS 창에 넣은 값을 그대로 확인된 정보로 돌려준다. */
export function answerPassVerification(
  body: Partial<PassVerificationRequestDto> | Record<string, unknown>,
  now = Date.now(),
): MockAnswer<IdentityVerificationDto> {
  const name = typeof body.name === 'string' ? body.name.trim() : ''
  const phone = typeof body.phone === 'string' ? body.phone.trim() : ''
  const birthDate = typeof body.birthDate === 'string' ? body.birthDate.trim() : ''
  const valid =
    CARRIERS.includes(String(body.carrier)) &&
    name.length > 0 &&
    PHONE_PATTERN.test(phone) &&
    DATE_PATTERN.test(birthDate)
  if (!valid) return fail(400, 'INVALID_REQUEST', '본인인증 정보를 확인해주세요.')
  return {
    status: 200,
    body: {
      status: 200,
      message: '본인 확인을 마쳤습니다.',
      data: issueToken({ method: 'PASS', phone, name, birthDate }, now),
    },
  }
}

/**
 * 가입 요청의 인증 토큰이 쓸 수 있는지 본다. 토큰이 없거나 · 지났거나 · 요청의 휴대폰 번호와 다르면 null.
 * PASS로 확인했으면 이름 · 생년월일도 같아야 한다.
 */
export function readMockVerificationToken(
  token: unknown,
  request: Record<string, unknown>,
  now = Date.now(),
): MockVerificationClaims | null {
  if (typeof token !== 'string') return null
  const claims = decode<MockVerificationClaims>(token, TOKEN_PREFIX)
  if (!claims || claims.expiresAt <= now) return null
  if (digits(claims.phone) !== digits(String(request.phone ?? ''))) return null
  if (claims.method === 'PASS') {
    if (claims.name !== String(request.name ?? '').trim()) return null
    if (claims.birthDate !== String(request.birthDate ?? '').trim()) return null
  }
  return claims
}
