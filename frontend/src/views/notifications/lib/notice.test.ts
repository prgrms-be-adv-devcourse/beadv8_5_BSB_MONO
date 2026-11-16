import { describe, expect, it } from 'vitest'

import type { Notice } from '@/entities/notification'

import { actionLabel, categoryOf, NOTICE_CATEGORIES } from './notice'

const notice = (kind: string) =>
  ({
    id: '1',
    kind,
    icon: 'Users',
    title: '파티에 초대받았어요',
    body: '본문',
    at: '방금',
    group: 'today',
    href: '/parties/join/token',
    sentAt: '2026-11-12T10:00:00+09:00',
    unread: true,
  }) as Notice

const crew = NOTICE_CATEGORIES.find((category) => category.value === 'crew')!

describe('파티 초대 알림 (BUK-182)', () => {
  it('크루 소식 묶음에 들어가고 버튼은 「파티 합류하기」다', () => {
    expect(categoryOf(notice('PARTY_INVITED'))).toBe('crew')
    expect(actionLabel(notice('PARTY_INVITED'), crew)).toBe('파티 합류하기')
  })

  it('다른 크루 알림은 묶음 문구를 그대로 쓴다', () => {
    expect(actionLabel(notice('CREW_JOINED'), crew)).toBe('크루 보러 가기')
  })
})
