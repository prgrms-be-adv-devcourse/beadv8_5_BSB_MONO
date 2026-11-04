import { getServerEnv } from './env'

// og:image · sitemap은 절대 주소여야 슬랙·카톡·검색엔진이 가져간다.
// Vercel 운영 주소는 VERCEL_PROJECT_PRODUCTION_URL로 들어오고, AWS는 SITE_URL로 준다.
export function siteUrl() {
  const { SITE_URL } = getServerEnv()
  // 끝의 /를 지운다. 그래야 `${siteUrl()}/races`가 //races가 되지 않는다
  if (SITE_URL) return SITE_URL.replace(/\/+$/, '')
  const vercel = process.env.VERCEL_PROJECT_PRODUCTION_URL
  return vercel ? `https://${vercel}` : 'http://localhost:3000'
}
