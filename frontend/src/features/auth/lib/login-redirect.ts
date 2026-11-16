import type { Route } from 'next'

const LOGIN_PATH = '/login'
/** 앱 내부 경로인지 확인할 때 기준으로 쓰는 가짜 주소. 실제로 요청하지 않는다. */
const BASE = 'http://crewrun.invalid'

/** 로그인 화면 주소. 로그인 뒤 돌아올 경로(경로+쿼리)를 ?next=로 넘긴다. */
export function loginHref(next: string): Route {
  return `${LOGIN_PATH}?next=${encodeURIComponent(next)}` as Route
}

type HistoryNavigation = {
  entries(): { key: string }[]
  currentEntry: { key: string } | null
  traverseTo(key: string): { finished: Promise<unknown> }
}

/**
 * 로그아웃한 뒤 문서를 새로 불러 홈으로 간다. 메모리의 토큰과 캐시가 함께 사라진다. 테스트에서 바꿔 끼울 수 있게 따로 둔다.
 * 지난 회원 화면 기록이 남으면 뒤로 갈 때마다 로그인 확인이 로그인 화면으로 보내므로,
 * 이 탭에서 앱을 처음 연 기록까지 되감은 뒤 그 자리를 홈으로 바꾼다. 그러면 뒤로 한 번에 앱 밖으로 나간다.
 * 처음 기록이 다른 문서(중간에 새로 불러온 적이 있음)면 그 화면이 새로 열리고 홈으로 바꾸지는 못하지만, 뒤로는 똑같이 한 번에 나간다.
 * shortcut: Navigation API가 없는 브라우저는 지금 자리만 홈으로 바꿔 반복이 남는다. 지원 브라우저가 충분히 늘면 이 분기를 지운다.
 */
export function reloadAfterLogout() {
  const goHome = () => window.location.replace('/')
  const nav = (window as { navigation?: HistoryNavigation }).navigation
  const first = nav?.entries()[0]
  if (!nav || !first || first.key === nav.currentEntry?.key) return goHome()
  nav.traverseTo(first.key).finished.then(goHome, goHome)
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

/** 서버·AI가 준 주소를 링크로 쓸 때: 사이트 안 경로(/로 시작)만 돌려주고, 그 밖의 값(https://…, //…)은 null */
export function safeInternalPath(path: string | null | undefined): Route | null {
  const safe = safeNextPath(path)
  return safe === '/' && path !== '/' ? null : safe
}
