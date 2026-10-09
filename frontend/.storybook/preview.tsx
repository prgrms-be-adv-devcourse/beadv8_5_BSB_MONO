import { withThemeByClassName } from '@storybook/addon-themes'
import type { Preview } from '@storybook/nextjs-vite'
import { Barlow_Condensed, Noto_Sans_KR } from 'next/font/google'

import { ToastProvider } from '../src/shared/ui'

import '../src/app/globals.css'

// app/layout.tsx와 같은 글꼴. Toast·Dialog처럼 body로 포털되는 요소도 글꼴을 받도록 html에 붙인다.
const notoSansKr = Noto_Sans_KR({ variable: '--font-noto-sans-kr', subsets: ['latin'] })
const barlowCondensed = Barlow_Condensed({
  variable: '--font-barlow-condensed',
  weight: ['600', '700', '800'],
  subsets: ['latin'],
})
document.documentElement.classList.add(notoSansKr.variable, barlowCondensed.variable, 'antialiased')
document.documentElement.lang = 'ko'

const preview: Preview = {
  parameters: {
    layout: 'padded',
    controls: { matchers: { color: /(background|color)$/i, date: /Date$/i } },
    // 배경은 globals.css의 body(bg-default)가 칠하므로 Storybook 배경 도구는 끈다.
    backgrounds: { disable: true },
  },
  decorators: [
    // 다크 모드는 피그마 Color 컬렉션의 Dark 모드. 앱처럼 .dark 클래스로만 켠다.
    withThemeByClassName({ themes: { light: '', dark: 'dark' }, defaultTheme: 'light' }),
    (Story) => (
      <ToastProvider>
        <Story />
      </ToastProvider>
    ),
  ],
  tags: ['autodocs'],
}

export default preview
