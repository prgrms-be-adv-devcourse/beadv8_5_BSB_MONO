import type { Route } from 'next'

/**
 * 초대 받기 화면(M10) 주소. 지금 열려 있는 사이트 주소를 앞에 붙인다.
 * 파티 참가 토큰을 주면 ?party=로 붙여, 크루 가입 뒤 그 파티 합류 화면으로 이어지는 링크 하나로 묶는다(BUK-98).
 */
export function inviteUrl(
  inviteToken: string,
  origin: string = window.location.origin,
  partyJoinToken?: string,
): string {
  const url = `${origin}/invite/${encodeURIComponent(inviteToken)}`
  return partyJoinToken ? `${url}?party=${encodeURIComponent(partyJoinToken)}` : url
}

export function partyJoinPath(partyJoinToken: string): Route {
  return `/parties/join/${encodeURIComponent(partyJoinToken)}` as Route
}
