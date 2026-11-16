import { getAccessToken } from './access-token'
import { ApiError } from './api-error'
import { tokenForRetry } from './auth-session'

const isServer = typeof window === 'undefined'

export interface CustomFetchInit extends RequestInit {
  /** 대회 등록·주문 생성·장바구니 결제·예치금 환급에 붙이는 Idempotency-Key 헤더 값 (`createIdempotencyKey()`로 만든다) */
  idempotencyKey?: string
}

/**
 * 모든 API 호출이 지나가는 fetch 래퍼. orval이 생성하는 코드도 이 함수를 쓴다.
 *
 * - 브라우저: 상대 경로 /api/... 그대로 → rewrites(로컬·Vercel) 또는 Nginx(AWS)
 * - 서버(RSC): 상대 경로를 쓸 수 없으므로 BACKEND_URL을 앞에 붙인다.
 *   API_MOCKING=on이면 네트워크를 타지 않고 MSW 핸들러에서 바로 응답을 받는다.
 *
 * 브라우저에서는 메모리의 Access Token을 `Authorization: Bearer` 헤더로 붙인다.
 * 401을 받으면 재발급(한 번만, single-flight) 뒤 원래 요청을 한 번 다시 보낸다. 재발급도 실패하면 로그아웃 상태가 되고
 * 처음 받은 401을 ApiError로 던진다. 로그인·재발급·로그아웃(/api/v1/auth/...)의 401은 재발급하지 않는다.
 *
 * 응답 본문(RsData)은 그대로 돌려준다. 생성된 타입·MSW 목이 RsData 모양이라 여기서 벗기면 서로 어긋난다.
 * data는 화면 쪽에서 `unwrapData`로 꺼낸다. 2xx가 아니면 ApiError(serverMessage, code)를 던진다.
 */
export async function customFetch<T>(url: string, init: CustomFetchInit = {}): Promise<T> {
  const { idempotencyKey, ...requestInit } = init
  if (idempotencyKey) {
    const headers = new Headers(requestInit.headers)
    headers.set('Idempotency-Key', idempotencyKey)
    requestInit.headers = headers
  }

  const response = isServer
    ? await serverFetch(url, requestInit)
    : await browserFetch(url, requestInit)

  const body = await parseBody(response)
  if (!response.ok) throw new ApiError(response.status, body, url)
  return body as T
}

/** 로그인·재발급·로그아웃. 여기의 401은 토큰 만료가 아니라 비밀번호 불일치·Refresh Token 무효라 재발급하지 않는다. */
const AUTH_PATH = '/api/v1/auth/'

async function browserFetch(url: string, init: RequestInit): Promise<Response> {
  const sentToken = getAccessToken()
  const response = await fetch(url, withAuthorization(init, sentToken))
  if (response.status !== 401 || isAuthPath(url)) return response

  const token = await tokenForRetry(sentToken)
  if (!token) return response
  return fetch(url, withAuthorization(init, token))
}

function withAuthorization(init: RequestInit, token: string | null): RequestInit {
  if (!token) return init
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  return { ...init, headers }
}

function isAuthPath(url: string): boolean {
  return new URL(url, window.location.origin).pathname.startsWith(AUTH_PATH)
}

/** 테스트용으로 내보낸다 */
export async function serverFetch(url: string, init: RequestInit): Promise<Response> {
  const { getServerEnv } = await import('@/shared/server-env')
  const env = getServerEnv()
  // cache: 'default'는 Next 16에서 옵션을 안 준 것과 같다(auto no cache). 동작은 그대로 두고 값만 드러낸다. 호출자 옵션이 이긴다.
  const request = new Request(new URL(url, env.BACKEND_URL), { cache: 'default', ...init })

  if (env.API_MOCKING === 'on') {
    const { resolveMockResponse } = await import('@/shared/mocks')
    return resolveMockResponse(request)
  }

  // Access Token은 브라우저 메모리에만 있어 서버는 토큰을 붙일 수 없다.
  // 로그인이 필요한 데이터는 서버 컴포넌트가 아니라 클라이언트에서 불러온다.
  return fetch(request)
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined
  const type = response.headers.get('content-type') ?? ''
  return type.includes('application/json') ? response.json() : response.text()
}
