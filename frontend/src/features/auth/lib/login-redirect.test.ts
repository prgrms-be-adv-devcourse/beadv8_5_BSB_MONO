import { describe, expect, it } from 'vitest'

import { loginHref, safeInternalPath, safeNextPath } from './login-redirect'

describe('safeNextPath', () => {
  it('앱 내부 경로는 그대로 쓴다', () => {
    expect(safeNextPath('/races/1')).toBe('/races/1')
  })

  it('쿼리가 붙은 경로도 그대로 쓴다', () => {
    expect(safeNextPath('/races/1/apply?option=2')).toBe('/races/1/apply?option=2')
  })

  it.each([
    ['//evil.com', '프로토콜 상대 주소'],
    ['https://evil.com', '스킴이 있는 주소'],
    ['javascript:alert(1)', '자바스크립트 스킴'],
    ['/\\evil.com', '역슬래시로 시작하는 주소'],
    ['/\t/evil.com', '제어 문자가 섞인 주소'],
  ])('%s(%s)는 홈으로 보낸다', (next) => {
    expect(safeNextPath(next)).toBe('/')
  })

  it.each([[''], [null], [undefined]])('값이 없으면(%s) 홈으로 보낸다', (next) => {
    expect(safeNextPath(next)).toBe('/')
  })

  it('로그인 화면 자체로 돌아가는 값은 홈으로 보낸다', () => {
    expect(safeNextPath('/login')).toBe('/')
    expect(safeNextPath('/login?next=%2Fme')).toBe('/')
  })
})

describe('loginHref', () => {
  it('돌아올 경로를 URL 인코딩해 ?next=로 붙이고, 로그인 화면에서 되읽으면 같은 경로다', () => {
    const href = loginHref('/races/1/apply?option=2')
    expect(href).toBe('/login?next=%2Fraces%2F1%2Fapply%3Foption%3D2')
    const next = new URL(href, 'http://localhost').searchParams.get('next')
    expect(safeNextPath(next)).toBe('/races/1/apply?option=2')
  })
})

describe('safeInternalPath', () => {
  it('사이트 안 경로만 돌려주고 그 밖은 null이다', () => {
    expect(safeInternalPath('/seller/kits')).toBe('/seller/kits')
    expect(safeInternalPath('/')).toBe('/')
    expect(safeInternalPath('https://evil.example')).toBeNull()
    expect(safeInternalPath('//evil.example')).toBeNull()
    expect(safeInternalPath('')).toBeNull()
  })
})
