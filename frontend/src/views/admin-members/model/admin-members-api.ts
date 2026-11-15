import { ApiError } from '@/shared/api'

// 관리자 회원 화면의 두 목록(확인이 필요한 계정, 고액 충전 확인)과 화면을 이어 주는 곳. 오류 문구는 서버가 주는 code로 정한다.

export const ADMIN_MEMBER_PAGE_SIZE = 20

export const ADMIN_MEMBER_ERRORS = {
  notAdmin: '관리자만 이용할 수 있어요.',
  memberUnavailable: '회원 정보를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  loadFailed: '목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
} as const

const BY_CODE: Record<string, string> = {
  CASH_ADMIN_FORBIDDEN: ADMIN_MEMBER_ERRORS.notAdmin,
  MEMBER_NOT_ADMIN: ADMIN_MEMBER_ERRORS.notAdmin,
  CASH_MEMBER_SERVICE_UNAVAILABLE: ADMIN_MEMBER_ERRORS.memberUnavailable,
}

export function loadErrorMessage(error: unknown): string {
  return (
    (error instanceof ApiError && error.code && BY_CODE[error.code]) ||
    ADMIN_MEMBER_ERRORS.loadFailed
  )
}

/** 2026-11-12T03:21:07 → 2026. 11. 12 03:21 (한국 시간 문자열이라 Date로 바꾸지 않고 자른다) */
export function toDateTime(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${y}. ${m}. ${d} ${iso.slice(11, 16)}`
}
