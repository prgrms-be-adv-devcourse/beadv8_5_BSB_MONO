import { describe, expect, it, vi } from 'vitest'

import { parseClientEnv } from './client-env'

// 브라우저용 값은 모든 화면이 받으므로 zod(압축 후 약 90KB)를 불러오면 안 된다
vi.mock('zod', () => {
  throw new Error('브라우저용 환경변수가 zod를 불러왔어요')
})

describe('parseClientEnv', () => {
  it('zod 없이 불러오고, 빈 문자열은 값 없음으로 보고 가짜 응답 모드는 기본 off다', () => {
    expect(
      parseClientEnv({ NEXT_PUBLIC_TOSS_CLIENT_KEY: '', NEXT_PUBLIC_GTM_ID: undefined }),
    ).toEqual({
      NEXT_PUBLIC_IMAGE_CDN_URL: undefined,
      NEXT_PUBLIC_TOSS_CLIENT_KEY: undefined,
      NEXT_PUBLIC_GTM_ID: undefined,
      NEXT_PUBLIC_CLARITY_ID: undefined,
      NEXT_PUBLIC_PORTONE_STORE_ID: undefined,
      NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY: undefined,
      NEXT_PUBLIC_KAKAO_JS_KEY: undefined,
      NEXT_PUBLIC_API_MOCKING: 'off',
    })
  })

  it('맞는 값은 그대로 돌려준다', () => {
    expect(
      parseClientEnv({
        NEXT_PUBLIC_IMAGE_CDN_URL: 'https://cdn.example.com',
        NEXT_PUBLIC_TOSS_CLIENT_KEY: 'test_ck_abc',
        NEXT_PUBLIC_GTM_ID: 'GTM-ABC123',
        NEXT_PUBLIC_CLARITY_ID: 'abc123',
        NEXT_PUBLIC_PORTONE_STORE_ID: 'store-1',
        NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY: 'channel-key',
        NEXT_PUBLIC_KAKAO_JS_KEY: '0123456789abcdef0123456789ABCDEF',
        NEXT_PUBLIC_API_MOCKING: 'on',
      }),
    ).toEqual({
      NEXT_PUBLIC_IMAGE_CDN_URL: 'https://cdn.example.com',
      NEXT_PUBLIC_TOSS_CLIENT_KEY: 'test_ck_abc',
      NEXT_PUBLIC_GTM_ID: 'GTM-ABC123',
      NEXT_PUBLIC_CLARITY_ID: 'abc123',
      NEXT_PUBLIC_PORTONE_STORE_ID: 'store-1',
      NEXT_PUBLIC_PORTONE_IDENTITY_CHANNEL_KEY: 'channel-key',
      NEXT_PUBLIC_KAKAO_JS_KEY: '0123456789abcdef0123456789ABCDEF',
      NEXT_PUBLIC_API_MOCKING: 'on',
    })
  })

  it('토스 운영 키를 넣으면 멈춘다', () => {
    expect(() => parseClientEnv({ NEXT_PUBLIC_TOSS_CLIENT_KEY: 'live_ck_abc' })).toThrow(
      '토스 클라이언트 키는 테스트 키(test_ck_)만 씁니다.',
    )
  })

  it('GTM ID가 GTM-으로 시작하지 않으면 멈춘다', () => {
    expect(() => parseClientEnv({ NEXT_PUBLIC_GTM_ID: 'G-123' })).toThrow(
      'GTM ID는 GTM-으로 시작합니다.',
    )
  })

  it('Clarity ID에 영문 · 숫자가 아닌 글자가 있으면 멈춘다', () => {
    expect(() => parseClientEnv({ NEXT_PUBLIC_CLARITY_ID: 'abc"/><script>' })).toThrow(
      'Clarity ID는 영문 · 숫자만 씁니다.',
    )
  })

  it('카카오 JavaScript 키가 32자리 16진수가 아니면 멈춘다', () => {
    expect(() => parseClientEnv({ NEXT_PUBLIC_KAKAO_JS_KEY: 'abc"/><script>' })).toThrow(
      '카카오 JavaScript 키는 32자리 16진수입니다.',
    )
  })

  it('CDN 주소가 URL 형식이 아니면 멈춘다', () => {
    expect(() => parseClientEnv({ NEXT_PUBLIC_IMAGE_CDN_URL: 'cdn.example.com' })).toThrow(
      'NEXT_PUBLIC_IMAGE_CDN_URL',
    )
  })

  it('가짜 응답 모드 값이 on · off가 아니면 멈춘다', () => {
    expect(() => parseClientEnv({ NEXT_PUBLIC_API_MOCKING: 'yes' })).toThrow(
      'NEXT_PUBLIC_API_MOCKING',
    )
  })
})
