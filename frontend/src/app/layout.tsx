import type { Metadata } from 'next'
import { Barlow_Condensed, Noto_Sans_KR } from 'next/font/google'

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

export const metadata: Metadata = {
  title: 'running',
  description: '러닝 대회 접수와 크루 단체 신청',
}

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html
      lang="ko"
      className={`${notoSansKr.variable} ${barlowCondensed.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
