import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '@/shared/mocks/node'

import { ApiError } from './api-error'
import { customFetch } from './fetcher'

// jsdom 환경이라 브라우저 경로를 탄다. 상대 경로는 jsdom 기본 주소(localhost)로 풀린다.
describe('customFetch', () => {
  it('MSW 핸들러의 JSON 응답을 돌려준다', async () => {
    await expect(customFetch('http://localhost/api/health')).resolves.toEqual({
      status: 'ok',
      mocked: true,
    })
  })

  it('2xx가 아니면 ApiError를 던진다', async () => {
    server.use(
      http.get('*/api/races', () => HttpResponse.json({ message: 'nope' }, { status: 409 })),
    )

    const error = await customFetch('http://localhost/api/races').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, body: { message: 'nope' } })
  })
})
