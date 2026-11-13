/** 파티 참가 링크 주소. 지금 열려 있는 사이트 주소를 앞에 붙인다. */
export function partyJoinUrl(joinToken: string, origin: string = window.location.origin): string {
  return `${origin}/parties/join/${encodeURIComponent(joinToken)}`
}
