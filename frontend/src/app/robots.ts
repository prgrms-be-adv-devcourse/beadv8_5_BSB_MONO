import type { MetadataRoute } from 'next'

import { getServerEnv, siteUrl } from '@/shared/server-env'

// 로그인해야 보이거나 개인 정보가 있는 주소. robots.txt는 앞부분이 같은 주소를 모두 막는다
// (/seller는 /seller-apply도 막는다)
const PRIVATE_PATHS = [
  '/me',
  '/tickets',
  '/cart',
  '/checkout',
  '/notifications',
  '/races/*/apply',
  '/races/*/queue',
  '/crews',
  '/invite',
  '/seller',
  '/admin',
  '/auth',
  '/login',
  '/signup',
  '/find-email',
  '/password-reset',
  '/forbidden',
  '/api/',
]

export default function robots(): MetadataRoute.Robots {
  // 가짜 응답 모드(로컬 · 시안 배포)의 대회는 실제 대회가 아니라서 아무것도 수집하지 않게 한다
  if (getServerEnv().API_MOCKING === 'on') {
    return { rules: { userAgent: '*', disallow: '/' } }
  }
  return {
    rules: { userAgent: '*', allow: '/', disallow: PRIVATE_PATHS },
    sitemap: `${siteUrl()}/sitemap.xml`,
  }
}
