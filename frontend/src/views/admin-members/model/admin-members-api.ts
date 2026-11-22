import { ApiError } from '@/shared/api'

// 관리자 회원 화면의 목록(전체 회원 찾기, 확인이 필요한 계정, 고액 충전 확인)과 화면을 이어 주는 곳. 오류 문구는 서버가 주는 code로 정한다.

export const ADMIN_MEMBER_PAGE_SIZE = 20

export const ADMIN_MEMBER_ERRORS = {
  notAdmin: '관리자만 이용할 수 있어요.',
  memberUnavailable: '회원 정보를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  loadFailed: '목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
  memberLoadFailed: '회원을 불러오지 못했어요',
} as const

const BY_CODE: Record<string, string> = {
  CASH_ADMIN_FORBIDDEN: ADMIN_MEMBER_ERRORS.notAdmin,
  MEMBER_NOT_ADMIN: ADMIN_MEMBER_ERRORS.notAdmin,
  CASH_MEMBER_SERVICE_UNAVAILABLE: ADMIN_MEMBER_ERRORS.memberUnavailable,
}

export function loadErrorMessage(
  error: unknown,
  fallback: string = ADMIN_MEMBER_ERRORS.loadFailed,
): string {
  return (error instanceof ApiError && error.code && BY_CODE[error.code]) || fallback
}

/** 2026-11-12T03:21:07 → 2026. 11. 12 03:21 (한국 시간 문자열이라 Date로 바꾸지 않고 자른다) */
export function toDateTime(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${y}. ${m}. ${d} ${iso.slice(11, 16)}`
}

/** 2025-03-02T09:00:00 → 2025. 3. 2 */
export function toShortDate(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${y}. ${Number(m)}. ${Number(d)}`
}

// 숫자를 읽었을 때 받침이 있으면(영·삼·육·칠·팔은 ㅇ·ㅁ·ㄱ·ㄹ) true. 1(일)·7(칠)·8(팔)은 ㄹ이라 「로」다.
const DIGIT_TAKES_EU: Record<string, boolean> = {
  '0': true,
  '1': false,
  '2': false,
  '3': true,
  '4': false,
  '5': false,
  '6': true,
  '7': false,
  '8': false,
  '9': false,
}

/** 끝 글자 받침에 맞는 조사 「으로」·「로」를 돌려준다. ㄹ 받침과 받침 없음은 「로」, 한글·숫자가 아니면 「로」. */
export function roParticle(word: string): '으로' | '로' {
  const last = word.at(-1) ?? ''
  const code = last.charCodeAt(0) - 0xac00
  const takesEu =
    code >= 0 && code < 11172 ? code % 28 !== 0 && code % 28 !== 8 : (DIGIT_TAKES_EU[last] ?? false)
  return takesEu ? '으로' : '로'
}
