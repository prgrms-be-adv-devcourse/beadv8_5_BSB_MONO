import { ApiError } from './api-error'

const isServer = typeof window === 'undefined'

/**
 * 모든 API 호출이 지나가는 fetch 래퍼. orval이 생성하는 코드도 이 함수를 쓴다.
 *
 * - 브라우저: 상대 경로 /api/... 그대로 → rewrites(로컬·Vercel) 또는 Nginx(AWS)
 * - 서버(RSC): 상대 경로를 쓸 수 없으므로 BACKEND_URL을 앞에 붙인다.
 *   API_MOCKING=on이면 네트워크를 타지 않고 MSW 핸들러에서 바로 응답을 받는다.
 */
export async function customFetch<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = isServer ? await serverFetch(url, init) : await fetch(url, init)

  const body = await parseBody(response)
  if (!response.ok) throw new ApiError(response.status, body, url)
  return body as T
}

async function serverFetch(url: string, init: RequestInit): Promise<Response> {
  const { getServerEnv } = await import('@/shared/config')
  const env = getServerEnv()
  const request = new Request(new URL(url, env.BACKEND_URL), init)

  if (env.API_MOCKING === 'on') {
    const { resolveMockResponse } = await import('@/shared/mocks')
    return resolveMockResponse(request)
  }

  // TODO(auth): 인증 방식이 정해지면 next/headers의 cookies()로 쿠키를 전달한다.
  return fetch(request)
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined
  const type = response.headers.get('content-type') ?? ''
  return type.includes('application/json') ? response.json() : response.text()
}
