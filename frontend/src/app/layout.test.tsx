// @vitest-environment node
import { describe, expect, it, vi } from 'vitest'

vi.mock('next/font/google', () => ({
  Noto_Sans_KR: () => ({ variable: 'noto' }),
  Barlow_Condensed: () => ({ variable: 'barlow' }),
}))
vi.mock('./_providers', () => ({ Providers: () => null }))
vi.mock('./globals.css', () => ({}))

const { metadata } = await import('./layout')

describe('루트 metadata', () => {
  it('상대 canonical을 쓰고, 절대 주소는 metadataBase가 만든다', () => {
    expect(metadata.metadataBase).toBeInstanceOf(URL)
    // './'는 페이지마다 자기 주소가 되어, 자체 canonical이 없는 화면이 홈으로 몰리지 않는다
    expect(metadata.alternates?.canonical).toBe('./')
  })
})
