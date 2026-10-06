import { NextResponse } from 'next/server'

/**
 * 판매자·관리자 화면 접근 1차 확인 자리. 지금은 모두 통과시킨다.
 * TODO(auth): 인증 방식(소셜 로그인 포함)이 정해지면 세션 쿠키 유무로 /login 리다이렉트.
 * 여기서는 가벼운 확인만 하고, 실제 권한 검사는 백엔드가 한다.
 */
export function proxy() {
  return NextResponse.next()
}

export const config = {
  matcher: ['/seller/:path*', '/admin/:path*'],
}
