import { afterEach, describe, expect, it, vi } from 'vitest'

import { serverFetch } from './fetcher'

vi.mock('@/shared/server-env', () => ({
  getServerEnv: () => ({ BACKEND_URL: 'http://backend.test', API_MOCKING: 'off' }),
}))

afterEach(() => vi.restoreAllMocks())

function spyFetch() {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'))
}

describe('serverFetch 캐시 옵션', () => {
  it('호출자가 cache를 안 주면 Next 기본값과 같은 default를 명시한다', async () => {
    const fetchSpy = spyFetch()
    await serverFetch('/api/v1/product/races', {})
    const request = fetchSpy.mock.calls[0][0] as Request
    expect(request.url).toBe('http://backend.test/api/v1/product/races')
    expect(request.cache).toBe('default')
  })

  it('호출자가 준 cache 옵션이 이긴다', async () => {
    const fetchSpy = spyFetch()
    await serverFetch('/api/v1/product/races', { cache: 'no-store' })
    expect((fetchSpy.mock.calls[0][0] as Request).cache).toBe('no-store')
  })
})
