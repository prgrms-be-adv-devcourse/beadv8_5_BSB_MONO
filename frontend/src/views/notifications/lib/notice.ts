import type { Route } from 'next'

import type { IconName } from '@/shared/ui'

import type { Notice, NoticeCategory } from '../api/use-notifications'

// 피그마 시나리오 37 · 02 알림 목록의 묶음 순서와 이름
export const NOTICE_CATEGORIES: {
  value: NoticeCategory
  title: string
  tab: string
  icon: IconName
}[] = [
  { value: 'race', title: '대회 소식', tab: '대회', icon: 'Calendar' },
  { value: 'payment', title: '결제 · 예치금', tab: '결제', icon: 'Wallet' },
  { value: 'crew', title: '크루 소식', tab: '크루', icon: 'Users' },
]

export function isNoticeCategory(value: string): value is NoticeCategory {
  return NOTICE_CATEGORIES.some((category) => category.value === value)
}

// 알림 시각은 회원이 있는 곳과 상관없이 서울 시각으로 보여 준다
const TIME_ZONE = 'Asia/Seoul'
const parts = (iso: string, options: Intl.DateTimeFormatOptions) =>
  Object.fromEntries(
    new Intl.DateTimeFormat('ko-KR', { timeZone: TIME_ZONE, ...options })
      .formatToParts(new Date(iso))
      .map((part) => [part.type, part.value]),
  )

/** 목록 날짜: 11.2 */
export function formatShortDate(iso: string) {
  const { month, day } = parts(iso, { month: 'numeric', day: 'numeric' })
  return `${month}.${day}`
}

/** 상세 날짜 칩: 2026.11.2 (월) */
export function formatDayChip(iso: string) {
  const { year, month, day, weekday } = parts(iso, {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
  })
  return `${year}.${month}.${day} (${weekday})`
}

/** 상세 시각: 오전 10:00 */
// 런타임마다 Intl의 오전·오후 표기가 달라(PM 등) 직접 만든다
export function formatTime(iso: string) {
  const { hour, minute } = parts(iso, { hour: 'numeric', minute: '2-digit', hourCycle: 'h23' })
  const h = Number(hour)
  return `${h < 12 ? '오전' : '오후'} ${h % 12 || 12}:${minute}`
}

const BASE = 'http://app.invalid'

/**
 * 서버가 준 이동 주소는 앱 안 화면 주소일 때만 쓴다. 바깥·스크립트 주소로 보내지 않기 위해서다.
 * 앞 글자만 보지 않고 브라우저처럼 해석해 본다(탭·줄바꿈을 지우면 //가 되는 주소를 막는다).
 * /api/는 화면이 아니라 미리 불러오기 요청이 나가므로 막는다.
 */
export function safeInternalHref(href: string | undefined): Route | undefined {
  if (!href?.startsWith('/')) return undefined
  const url = new URL(href, BASE)
  if (url.origin !== BASE || url.pathname.startsWith('/api/')) return undefined
  return `${url.pathname}${url.search}${url.hash}` as Route
}

export const byOldest = (a: Notice, b: Notice) => Date.parse(a.sentAt) - Date.parse(b.sentAt)
