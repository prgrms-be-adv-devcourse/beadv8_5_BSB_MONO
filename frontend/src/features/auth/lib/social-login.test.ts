import { describe, expect, it } from 'vitest'

import {
  socialAuthorizeHref,
  socialLinkResultMessage,
  socialLoginErrorMessage,
  socialProviderOf,
} from './social-login'

describe('socialAuthorizeHref', () => {
  it('로그인은 next를 앱 내부 경로로 고쳐 시작 주소에 싣는다', () => {
    expect(socialAuthorizeHref('kakao', { next: '/races/12?tab=a' })).toBe(
      '/api/v1/auth/oauth2/authorize/kakao?next=%2Fraces%2F12%3Ftab%3Da',
    )
    expect(socialAuthorizeHref('google', { next: 'https://evil.com' })).toBe(
      '/api/v1/auth/oauth2/authorize/google?next=%2F',
    )
    expect(socialAuthorizeHref('google')).toBe('/api/v1/auth/oauth2/authorize/google')
  })

  it('계정 연결도 연결 표 없이 돌아올 화면만 싣는다 (표는 쿠키로 간다)', () => {
    expect(socialAuthorizeHref('kakao', { next: '/me/accounts' })).toBe(
      '/api/v1/auth/oauth2/authorize/kakao?next=%2Fme%2Faccounts',
    )
  })
})

describe('socialLoginErrorMessage', () => {
  it('같은 이메일이면 원래 방법으로 로그인한 뒤 계정 연결하는 길을 알려 준다', () => {
    expect(socialLoginErrorMessage('AUTH_SOCIAL_EMAIL_TAKEN', 'google', 'email')).toBe(
      '이메일로 가입한 계정이에요. 이메일로 로그인해 주세요. 마이 > 계정 연결에서 구글을 연결할 수 있어요.',
    )
    expect(socialLoginErrorMessage('AUTH_SOCIAL_EMAIL_TAKEN', 'google', 'kakao')).toBe(
      '카카오로 가입한 이메일이에요. 카카오로 로그인해 주세요. 마이 > 계정 연결에서 구글을 연결할 수 있어요.',
    )
  })

  it('그 밖의 실패는 다시 시도하라고 알리고, 오류가 없으면 아무것도 보이지 않는다', () => {
    expect(socialLoginErrorMessage('AUTH_SOCIAL_FAILED')).toBe(
      '소셜 로그인을 끝내지 못했어요. 다시 시도해 주세요.',
    )
    expect(socialLoginErrorMessage(undefined)).toBeUndefined()
  })
})

describe('socialLinkResultMessage', () => {
  it('연결 결과를 알린다', () => {
    expect(socialLinkResultMessage('kakao', undefined)).toEqual({
      tone: 'success',
      message: '카카오 계정을 연결했어요.',
    })
    expect(socialLinkResultMessage(undefined, 'AUTH_SOCIAL_ALREADY_LINKED')?.message).toBe(
      '이미 다른 회원에 연결된 소셜 계정이에요.',
    )
    expect(socialLinkResultMessage(undefined, undefined)).toBeUndefined()
  })
})

describe('socialProviderOf', () => {
  it('주소 값(kakao)과 응답 값(KAKAO) 모두 찾는다', () => {
    expect(socialProviderOf('kakao')?.label).toBe('카카오')
    expect(socialProviderOf('GOOGLE')?.label).toBe('구글')
    expect(socialProviderOf('naver')).toBeUndefined()
  })
})
