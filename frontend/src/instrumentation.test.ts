import { afterEach, describe, expect, it, vi } from 'vitest'

import { onRequestError } from './instrumentation'

afterEach(() => vi.restoreAllMocks())

const request = { path: '/races/1?token=secret', method: 'GET', headers: { cookie: 'x' } }
const context = {
  routerKind: 'App Router',
  routePath: '/races/[id]',
  routeType: 'render',
  renderSource: 'react-server-components',
  revalidateReason: undefined,
  renderType: 'dynamic',
} as const

describe('onRequestError', () => {
  it('경로(조회 값 제외) · 메서드 · 오류 문구 · digest를 JSON 한 줄로 남긴다', async () => {
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    await onRequestError(Object.assign(new Error('db down'), { digest: 'd9' }), request, context)

    expect(log).toHaveBeenCalledTimes(1)
    const line = log.mock.calls[0][0] as string
    expect(line).not.toContain('\n')
    expect(JSON.parse(line)).toMatchObject({
      event: 'request_error',
      path: '/races/1',
      method: 'GET',
      message: 'db down',
      digest: 'd9',
    })
    expect(line).not.toContain('secret')
    expect(line).not.toContain('cookie')
  })

  it('Error가 아닌 값을 던져도 기록한다', async () => {
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    await onRequestError('oops', request, context)
    expect(JSON.parse(log.mock.calls[0][0] as string)).toMatchObject({ message: 'oops' })
  })
})
