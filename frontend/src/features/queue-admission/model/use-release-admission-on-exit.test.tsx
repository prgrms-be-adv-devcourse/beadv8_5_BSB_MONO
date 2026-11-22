import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'

import { useReleaseAdmissionOnExit, type AdmissionTarget } from './use-release-admission-on-exit'

const RACE: AdmissionTarget = { line: 'individual', raceId: 33, raceCourseId: 67 }
const PARTY: AdmissionTarget = { line: 'party', raceId: 33, raceCourseId: 67 }
const RACE_URL = '/api/v1/queue/races/33/racecourses/67/active'
const PARTY_URL = '/api/v1/queue/parties/races/33/racecourses/67/active'

const ok = () => new Response(JSON.stringify({ status: 200, message: '', data: null }))
let fetchSpy: MockInstance<typeof fetch>

/** 해제 요청(DELETE)만 센다 */
const releases = () => fetchSpy.mock.calls.filter(([, init]) => init?.method === 'DELETE')

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => ok())
})
afterEach(() => {
  fetchSpy.mockRestore()
  window.history.pushState({}, '', '/')
})

/** 앱 안 이동: 주소를 바꾼 뒤 화면을 내린다 (Next는 새 화면을 그린 뒤에 옛 화면의 정리를 부른다) */
function leaveTo(path: string, unmount: () => void) {
  window.history.pushState({}, '', path)
  unmount()
}

describe('useReleaseAdmissionOnExit', () => {
  it('신청서에서 대회 화면으로 나가면 개인 입장 해제를 keepalive로 부른다', () => {
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))
    leaveTo('/races/33', unmount)

    expect(releases()).toHaveLength(1)
    const [url, init] = releases()[0] as [string, RequestInit]
    expect(url).toBe(RACE_URL)
    expect(init.keepalive).toBe(true)
  })

  it('신청서 → 결제 화면으로 넘어갈 때는 해제하지 않는다', () => {
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))
    leaveTo('/checkout', unmount)

    expect(releases()).toHaveLength(0)
  })

  it('결제 화면에서 신청서로 돌아갈 때도 해제하지 않는다 (같은 입장 안의 이동)', () => {
    window.history.pushState({}, '', '/checkout')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))
    leaveTo('/races/33/apply', unmount)

    expect(releases()).toHaveLength(0)
  })

  it('결제 화면에서 다른 화면으로 나가면 해제한다', () => {
    window.history.pushState({}, '', '/checkout')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))
    leaveTo('/me/tickets', unmount)

    expect(releases()).toHaveLength(1)
  })

  it('탭을 닫거나 새로고침하면(pagehide) 해제한다', () => {
    window.history.pushState({}, '', '/races/33/apply')
    renderHook(() => useReleaseAdmissionOnExit(RACE))
    window.dispatchEvent(new Event('pagehide'))

    expect(releases()).toHaveLength(1)
    expect((releases()[0][1] as RequestInit).keepalive).toBe(true)
  })

  it('keep이면 pagehide에도 해제하지 않는다 (토스 결제창으로 떠나는 중)', () => {
    window.history.pushState({}, '', '/checkout')
    renderHook(() => useReleaseAdmissionOnExit(RACE, { keep: true }))
    window.dispatchEvent(new Event('pagehide'))

    expect(releases()).toHaveLength(0)
  })

  it('keep이 나중에 켜져도 그 값을 따른다', () => {
    window.history.pushState({}, '', '/checkout')
    const { rerender } = renderHook(({ keep }) => useReleaseAdmissionOnExit(RACE, { keep }), {
      initialProps: { keep: false },
    })
    rerender({ keep: true })
    window.dispatchEvent(new Event('pagehide'))

    expect(releases()).toHaveLength(0)
  })

  it('대상이 없으면(시안 미리보기 · 아직 모름) 부르지 않는다', () => {
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(undefined))
    window.dispatchEvent(new Event('pagehide'))
    leaveTo('/races/33', unmount)

    expect(releases()).toHaveLength(0)
  })

  it('대상이 나중에 정해지면 그때부터 해제한다 (결제 화면은 주문을 받은 뒤에 종목을 안다)', () => {
    window.history.pushState({}, '', '/checkout')
    const { rerender, unmount } = renderHook(
      ({ target }: { target?: AdmissionTarget }) => useReleaseAdmissionOnExit(target),
      { initialProps: {} as { target?: AdmissionTarget } },
    )
    rerender({ target: RACE })
    leaveTo('/me/tickets', unmount)

    expect(releases()).toHaveLength(1)
  })

  it('해제 요청이 실패해도 화면 이동을 막지 않고 오류를 밖으로 내지 않는다', async () => {
    fetchSpy.mockImplementation(async () => {
      throw new TypeError('network')
    })
    const unhandled = vi.fn()
    process.on('unhandledRejection', unhandled)
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))

    expect(() => leaveTo('/races/33', unmount)).not.toThrow()
    expect(() => window.dispatchEvent(new Event('pagehide'))).not.toThrow()
    await new Promise((resolve) => setTimeout(resolve, 0))
    process.off('unhandledRejection', unhandled)
    expect(unhandled).not.toHaveBeenCalled()
  })

  it('서버가 오류 응답(503)을 줘도 오류를 밖으로 내지 않는다', async () => {
    fetchSpy.mockImplementation(async () => new Response('{}', { status: 503 }))
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))

    expect(() => leaveTo('/races/33', unmount)).not.toThrow()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(releases()).toHaveLength(1)
  })

  it('화면을 내린 뒤의 pagehide는 다시 해제하지 않는다', () => {
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE))
    leaveTo('/races/33', unmount)
    window.dispatchEvent(new Event('pagehide'))

    expect(releases()).toHaveLength(1)
  })

  it('파티 신청 화면은 파티 API로 해제한다', () => {
    window.history.pushState({}, '', '/parties/5/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(PARTY))
    leaveTo('/parties/5', unmount)

    expect(releases()).toHaveLength(1)
    expect(releases()[0][0]).toBe(PARTY_URL)
  })

  it('파티 신청 화면에서 pagehide도 파티 API로 해제한다', () => {
    window.history.pushState({}, '', '/parties/5/apply')
    renderHook(() => useReleaseAdmissionOnExit(PARTY))
    window.dispatchEvent(new Event('pagehide'))

    expect(releases()[0][0]).toBe(PARTY_URL)
  })

  it('개발 모드 이중 실행(StrictMode)에서도 같은 주소면 해제하지 않는다', () => {
    window.history.pushState({}, '', '/races/33/apply')
    const { unmount } = renderHook(() => useReleaseAdmissionOnExit(RACE), {
      reactStrictMode: true,
    })
    expect(releases()).toHaveLength(0)
    unmount()
    expect(releases()).toHaveLength(0)
  })
})
