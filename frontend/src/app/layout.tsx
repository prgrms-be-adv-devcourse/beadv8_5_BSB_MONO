import { GoogleTagManager } from '@next/third-parties/google'
import type { Metadata, Viewport } from 'next'
import { Barlow_Condensed, Noto_Sans_KR } from 'next/font/google'

import { BASE_OPEN_GRAPH, siteVerification } from '@/shared/config'
import { ClarityTag, cn, trackingClarityId, trackingGtmId } from '@/shared/lib'
import { siteUrl } from '@/shared/server-env'

import { Providers } from './_providers'

import './globals.css'

// 피그마 텍스트 스타일의 글꼴. 본문은 Noto Sans KR, Display·Eyebrow는 Barlow Condensed
const notoSansKr = Noto_Sans_KR({
  variable: '--font-noto-sans-kr',
  subsets: ['latin'],
})

const barlowCondensed = Barlow_Condensed({
  variable: '--font-barlow-condensed',
  weight: ['600', '700', '800'],
  subsets: ['latin'],
})

const SITE_NAME = '크루런'
const DESCRIPTION = '러닝 대회 참가권 마켓플레이스'

// 아이콘(favicon.ico, icon.svg, apple-icon.png)과 opengraph-image.png는
// app/ 폴더의 파일 규칙으로 Next가 태그를 자동으로 붙인다.
export const metadata: Metadata = {
  metadataBase: new URL(siteUrl()),
  // './'는 페이지마다 자기 주소가 된다. 화면이 자체 canonical을 정하면 그쪽이 우선한다
  alternates: { canonical: './' },
  title: { default: SITE_NAME, template: `%s | ${SITE_NAME}` },
  description: DESCRIPTION,
  applicationName: SITE_NAME,
  appleWebApp: { title: SITE_NAME },
  openGraph: { ...BASE_OPEN_GRAPH, title: SITE_NAME, description: DESCRIPTION, url: '/' },
  twitter: { card: 'summary_large_image' },
  verification: siteVerification(),
}

export const viewport: Viewport = {
  themeColor: '#0057FF',
}

export default function RootLayout({ children }: LayoutProps<'/'>) {
  // 구매자 · 판매자 센터 · 어드민이 모두 이 레이아웃을 지난다. 어드민은 GTM 실행 조건과 trackEvent에서 뺀다
  const gtmId = trackingGtmId()
  const clarityId = trackingClarityId()
  return (
    <html
      lang="ko"
      className={cn(notoSansKr.variable, barlowCondensed.variable, 'h-full antialiased')}
    >
      {gtmId && <GoogleTagManager gtmId={gtmId} />}
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
        {clarityId && <ClarityTag id={clarityId} />}
      </body>
    </html>
  )
}
