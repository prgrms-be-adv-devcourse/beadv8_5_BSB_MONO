/** 로그인해야 열리는 회원 화면의 앞부분. 가짜 응답 모드에서 비로그인으로 열면 로그인 화면으로 보낼 때 쓴다. */
// 크루 화면(/crews)과 초대 받기(/invite)는 크루 API가 모두 로그인을 요구한다(백엔드 결정 BE-028).
const PROTECTED_PREFIXES = [
  '/me',
  '/tickets',
  '/cart',
  '/checkout',
  '/notifications',
  '/crews',
  '/invite',
  '/parties',
  '/community/write',
]

/** 대회 신청 화면(/races/1/apply)도 로그인이 필요하다. */
const APPLY_PATH = /^\/races\/[^/]+\/apply(\/|$)/

/** 글 수정 화면(/community/12/edit). 글 목록·상세는 로그인 없이 열린다. */
const COMMUNITY_EDIT_PATH = /^\/community\/[^/]+\/edit(\/|$)/

/** 탈퇴 완료 화면. 탈퇴하면 토큰을 비운 뒤 오므로 로그인을 요구하면 로그인 화면으로 밀려난다. */
const WITHDRAW_DONE_PATH = '/me/withdraw/done'

/**
 * 고객센터. 로그인 화면 하단 링크가 가는 곳이라 로그아웃 상태로 열려야 한다. 개인 정보는 없고(정적 화면),
 * 안의 문의 쓰기·내 문의(/me/inquiries)는 그대로 로그인이 필요하다.
 */
const SUPPORT_PATH = '/me/support'

export function isProtectedPath(pathname: string): boolean {
  if (pathname === WITHDRAW_DONE_PATH || pathname === SUPPORT_PATH) return false
  return (
    APPLY_PATH.test(pathname) ||
    COMMUNITY_EDIT_PATH.test(pathname) ||
    PROTECTED_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
  )
}
