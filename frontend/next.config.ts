import type { NextConfig } from 'next'

import { clientEnv, getServerEnv } from './src/shared/config/env'

// next.config는 빌드할 때 한 번 읽혀 결과물에 고정된다.
// 그래서 API_MOCKING·이미지 설정을 바꾸려면 다시 빌드해야 한다.
const env = getServerEnv()

const nextConfig: NextConfig = {
  // AWS(Docker)용. Vercel은 이 값을 무시하므로 그대로 둬도 된다.
  output: 'standalone',
  typedRoutes: true,

  images: clientEnv.NEXT_PUBLIC_IMAGE_CDN_URL
    ? { loader: 'custom', loaderFile: './src/shared/lib/image/cdn-loader.ts' }
    : {
        remotePatterns: env.IMAGE_REMOTE_HOSTS.map((hostname) => ({
          protocol: 'https',
          hostname,
        })),
      },

  // 브라우저는 항상 /api/... 로 부른다.
  // - API_MOCKING=on : rewrites 없음 → src/app/api/[[...path]] 가 MSW 핸들러로 응답
  // - API_MOCKING=off: 여기서 BACKEND_URL로 넘긴다 (로컬·Vercel)
  // - AWS: Nginx가 /api를 백엔드로 먼저 보내므로 이 규칙까지 오지 않는다
  async rewrites() {
    if (env.API_MOCKING === 'on') return []
    return [{ source: '/api/:path*', destination: `${env.BACKEND_URL}/api/:path*` }]
  },
}

export default nextConfig
