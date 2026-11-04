import type { Metadata } from 'next'

import { clientEnv } from './client-env'

/**
 * 공유 카드 공통값. 페이지가 openGraph를 정하면 루트 값이 통째로 바뀌므로 페이지에서도 펼쳐 넣는다.
 * 이미지는 루트의 opengraph-image.png 파일이 붙이므로 여기 넣지 않는다.
 */
export const BASE_OPEN_GRAPH = {
  type: 'website',
  siteName: '크루런',
  locale: 'ko_KR',
} satisfies Metadata['openGraph']

/** 페이지가 openGraph를 정하면 루트의 opengraph-image.png가 붙지 않아 직접 넣는다 */
export const DEFAULT_OG_IMAGE = '/opengraph-image.png'

type VerificationEnv = {
  NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION?: string
  NEXT_PUBLIC_NAVER_SITE_VERIFICATION?: string
}

/** 구글 Search Console · 네이버 서치어드바이저 소유 확인 태그. 값이 있는 것만 넣는다 */
export function siteVerification(env: VerificationEnv = clientEnv): Metadata['verification'] {
  const google = env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION
  const naver = env.NEXT_PUBLIC_NAVER_SITE_VERIFICATION
  if (!google && !naver) return undefined
  return {
    ...(google ? { google } : {}),
    ...(naver ? { other: { 'naver-site-verification': naver } } : {}),
  }
}
