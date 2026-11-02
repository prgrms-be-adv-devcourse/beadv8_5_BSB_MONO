import type { BadgeTone } from '@/shared/ui'

/** 크루 역할. 크루 API의 myRole · role 값이다. */
export type CrewRole = 'LEADER' | 'MANAGER' | 'MEMBER'

export const CREW_ROLE_LABEL: Record<CrewRole, string> = {
  LEADER: '크루장',
  MANAGER: '운영진',
  MEMBER: '크루원',
}

// 역할은 상태가 아니라서 안내(info) 색을 쓰지 않는다. 크루장만 brand, 나머지는 neutral(10/30 결정).
export const CREW_ROLE_TONE: Record<CrewRole, BadgeTone> = {
  LEADER: 'brand',
  MANAGER: 'neutral',
  MEMBER: 'neutral',
}

/** 초대 링크는 크루장과 운영진만 만든다(정책 7장 「역할과 초대」 1번). */
export function canInvite(role: CrewRole): boolean {
  return role !== 'MEMBER'
}
