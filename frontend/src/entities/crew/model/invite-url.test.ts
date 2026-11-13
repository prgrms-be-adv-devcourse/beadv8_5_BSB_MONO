import { describe, expect, it } from 'vitest'

import { inviteUrl, partyJoinPath } from './invite-url'

describe('inviteUrl', () => {
  it('파티 토큰이 없으면 지금처럼 초대 주소만 만든다', () => {
    expect(inviteUrl('q7Xk', 'https://crewrun.kr')).toBe('https://crewrun.kr/invite/q7Xk')
  })

  it('파티 토큰이 있으면 ?party=로 붙여 링크 하나로 묶는다', () => {
    expect(inviteUrl('q7Xk', 'https://crewrun.kr', 'a/b?c')).toBe(
      'https://crewrun.kr/invite/q7Xk?party=a%2Fb%3Fc',
    )
  })
})

describe('partyJoinPath', () => {
  it('토큰을 인코딩해 파티 합류 경로를 만든다', () => {
    expect(partyJoinPath('a/b')).toBe('/parties/join/a%2Fb')
  })
})
