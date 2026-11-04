import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { syncClarity, trackingClarityId } from './clarity'

const env = vi.hoisted(() => ({
  NEXT_PUBLIC_CLARITY_ID: 'abc123' as string | undefined,
  NEXT_PUBLIC_API_MOCKING: 'off' as 'on' | 'off',
}))
vi.mock('@/shared/config', () => ({ clientEnv: env }))

const clarityScripts = () => document.querySelectorAll('script[src^="https://www.clarity.ms/tag/"]')

beforeEach(() => {
  env.NEXT_PUBLIC_CLARITY_ID = 'abc123'
  env.NEXT_PUBLIC_API_MOCKING = 'off'
})

afterEach(() => {
  // 다음 테스트가 「멈춘 상태」를 물려받지 않게 구매자 화면으로 돌려 둔 뒤 지운다
  if (window.clarity) syncClarity('abc123', '/')
  delete window.clarity
  clarityScripts().forEach((script) => script.remove())
})

describe('trackingClarityId', () => {
  it('Clarity ID가 없거나 가짜 응답 모드면 Clarity를 넣지 않는다', () => {
    expect(trackingClarityId()).toBe('abc123')
    env.NEXT_PUBLIC_API_MOCKING = 'on'
    expect(trackingClarityId()).toBeUndefined()
    env.NEXT_PUBLIC_API_MOCKING = 'off'
    env.NEXT_PUBLIC_CLARITY_ID = undefined
    expect(trackingClarityId()).toBeUndefined()
  })
})

describe('syncClarity', () => {
  it('구매자 화면에서 Clarity 스크립트를 한 번만 넣는다', () => {
    syncClarity('abc123', '/races/7')
    syncClarity('abc123', '/me')

    const scripts = clarityScripts()
    expect(scripts).toHaveLength(1)
    expect(scripts[0]).toHaveAttribute('src', 'https://www.clarity.ms/tag/abc123')
    expect(typeof window.clarity).toBe('function')
  })

  it('어드민 화면으로 처음 들어오면 스크립트를 넣지 않는다', () => {
    syncClarity('abc123', '/admin')
    syncClarity('abc123', '/admin/members')

    expect(clarityScripts()).toHaveLength(0)
    expect(window.clarity).toBeUndefined()
  })

  it('구매자 화면에서 어드민으로 옮기면 기록을 멈추고, 다시 나오면 이어서 기록한다', () => {
    syncClarity('abc123', '/me')
    const clarity = vi.fn()
    window.clarity = clarity

    syncClarity('abc123', '/admin/members')
    syncClarity('abc123', '/admin/races')
    expect(clarity.mock.calls).toEqual([['stop']])

    syncClarity('abc123', '/races')
    syncClarity('abc123', '/races/7')
    expect(clarity.mock.calls).toEqual([['stop'], ['start']])
  })

  it('/admin으로 시작하지만 어드민이 아닌 주소는 기록한다', () => {
    syncClarity('abc123', '/administrator')
    expect(clarityScripts()).toHaveLength(1)
  })

  it('Clarity가 예외를 던져도 화면 동작을 멈추지 않는다', () => {
    syncClarity('abc123', '/me')
    window.clarity = () => {
      throw new Error('blocked')
    }
    expect(() => syncClarity('abc123', '/admin')).not.toThrow()
  })
})
