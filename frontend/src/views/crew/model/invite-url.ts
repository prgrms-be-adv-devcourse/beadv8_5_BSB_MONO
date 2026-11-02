/** 초대 받기 화면(M10) 주소. 지금 열려 있는 사이트 주소를 앞에 붙인다. */
export function inviteUrl(inviteToken: string, origin: string = window.location.origin): string {
  return `${origin}/invite/${encodeURIComponent(inviteToken)}`
}
