import type { Route } from 'next'

const LOGIN_PATH = '/login'
/** 앱 내부 경로인지 확인할 때 기준으로 쓰는 가짜 주소. 실제로 요청하지 않는다. */
const BASE = 'http://crewrun.invalid'

/** 로그인 화면 주소. 로그인 뒤 돌아올 경로(경로+쿼리)를 ?next=로 넘긴다. */
export function loginHref(next: string): Route {
  return `${LOGIN_PATH}?next=${encodeURIComponent(next)}` as Route
}

/** 문서를 새로 불러 로그인 화면으로 간다. 메모리의 토큰과 캐시가 함께 사라진다. 테스트에서 바꿔 끼울 수 있게 따로 둔다. */
export function reloadToLogin() {
  window.location.replace(LOGIN_PATH)
}

/** 문서를 새로 불러 path로 간다 (탈퇴 완료). 테스트에서 바꿔 끼울 수 있게 따로 둔다. */
export function reloadTo(path: Route) {
  window.location.replace(path)
}

/**
 * 로그인 뒤 보낼 경로. 앱 내부 경로만 그대로 쓰고, 아니면 '/'(홈)로 보낸다.
 * ?next=에 다른 사이트 주소를 넣어 로그인 직후 그쪽으로 보내는 공격(오픈 리다이렉트)을 막는다.
 * - '/'로 시작해야 한다 ('https://…', 'javascript:…' 같은 스킴은 여기서 걸린다)
 * - '//evil.com', '/\evil.com'은 브라우저가 다른 사이트로 읽으므로 막는다
 * - 공백·제어 문자는 브라우저가 지워서 '//'가 될 수 있어 막는다
 * - 로그인 화면 자체로 돌아가는 값은 홈으로 바꾼다
 */
export function safeNextPath(next: string | null | undefined): Route {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/'
  if (/[\s\u0000-\u001f\u007f]/.test(next)) return '/'
  let url: URL
  try {
    url = new URL(next, BASE)
  } catch {
    return '/'
  }
  if (url.origin !== BASE || url.pathname === LOGIN_PATH) return '/'
  return `${url.pathname}${url.search}${url.hash}` as Route
}
