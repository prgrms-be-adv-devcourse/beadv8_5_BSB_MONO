// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest'

// 환경변수는 처음 읽을 때 고정되므로, 모드마다 모듈을 새로 불러온다
async function loadRobots(mocking: 'on' | 'off') {
  vi.resetModules()
  vi.stubEnv('API_MOCKING', mocking)
  vi.stubEnv('SITE_URL', 'https://crewrun.example')
  return (await import('./robots')).default()
}

// robots.txt 규칙처럼 앞부분이 같으면 막고, *는 아무 글자로 본다
function blocked(rules: string | string[] | undefined, path: string) {
  const list = rules === undefined ? [] : [rules].flat()
  return list.some((rule) =>
    new RegExp(`^${rule.replaceAll('*', '.*').replaceAll('?', '\\?')}`).test(path),
  )
}

afterEach(() => vi.unstubAllEnvs())

describe('robots', () => {
  it('실제 운영 모드는 로그인이 필요한 주소만 막고 sitemap 주소를 알려 준다', async () => {
    const robots = await loadRobots('off')
    const rule = [robots.rules].flat()[0]

    expect(rule.userAgent).toBe('*')
    for (const path of [
      '/me',
      '/me/deposit',
      '/tickets/3',
      '/cart',
      '/checkout',
      '/notifications',
      '/races/1/apply',
      '/races/1/queue',
      '/crews/2',
      '/invite/abc',
      '/seller',
      '/seller/races',
      '/seller-apply',
      '/admin/members',
      '/auth/social/complete',
      '/login',
      '/signup',
      '/api/v1/product/races',
    ]) {
      expect(blocked(rule.disallow, path), path).toBe(true)
    }
    for (const path of ['/', '/races', '/races/1', '/terms/service']) {
      expect(blocked(rule.disallow, path), path).toBe(false)
    }
    expect(robots.sitemap).toBe('https://crewrun.example/sitemap.xml')
  })

  it('가짜 응답 모드는 모든 주소의 수집을 막는다', async () => {
    const robots = await loadRobots('on')
    const rule = [robots.rules].flat()[0]

    expect(blocked(rule.disallow, '/')).toBe(true)
    expect(blocked(rule.disallow, '/races/1')).toBe(true)
    expect(robots.sitemap).toBeUndefined()
  })
})
