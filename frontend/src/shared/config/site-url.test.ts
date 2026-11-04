// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest'

// 서버 환경변수는 처음 읽을 때 고정되므로, 값마다 모듈을 새로 불러온다
async function siteUrlWith(value: string) {
  vi.resetModules()
  vi.stubEnv('SITE_URL', value)
  return (await import('./site-url')).siteUrl()
}

afterEach(() => vi.unstubAllEnvs())

describe('siteUrl', () => {
  it('SITE_URL 끝의 /를 지워 sitemap 주소가 //races가 되지 않게 한다', async () => {
    expect(await siteUrlWith('https://crewrun.example/')).toBe('https://crewrun.example')
    expect(await siteUrlWith('https://crewrun.example')).toBe('https://crewrun.example')
  })
})
