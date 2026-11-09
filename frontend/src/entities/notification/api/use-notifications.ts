'use client'

import { useQuery } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'

import {
  customFetch,
  getAuthStatus,
  subscribeAuthStatus,
  unwrapData,
  type RsData,
} from '@/shared/api'

/** 내 알림 하나 (알림 서비스 NoticeDto) */
export type Notice = {
  id: string
  /** 서버 알림 종류 이름 (예: CREW_JOINED). 화면이 모르는 종류도 올 수 있다 */
  kind: string
  icon: string
  title: string
  body: string
  /** 서버가 정한 짧은 시각 (방금, 5시간 전, 11/2) */
  at: string
  group: 'today' | 'earlier'
  /** 누르면 갈 앱 안 주소. 없으면 null */
  href: string | null
  /** 보낸 시각 (서울 시각 ISO 8601, +09:00) */
  sentAt: string
  unread: boolean
}

const BASE = '/api/v1/notification/notifications'
export const NOTIFICATIONS_KEY = ['notifications']

/**
 * 내 알림 (GET /api/v1/notification/notifications). 실시간 연결 없이 30초마다 다시 불러온다.
 * 로그인 확인이 끝난 뒤에만 부른다. features/auth의 useAuthStatus와 같은 값을 아래 층에서 직접 구독한다.
 */
export function useNotifications() {
  const enabled =
    useSyncExternalStore(subscribeAuthStatus, getAuthStatus, () => 'pending' as const) ===
    'authenticated'
  return useQuery({
    queryKey: NOTIFICATIONS_KEY,
    queryFn: async () => unwrapData(await customFetch<RsData<Notice[]>>(BASE)),
    enabled,
    retry: false,
    refetchInterval: 30_000,
  })
}

/** 안 읽은 알림이 하나라도 있는지. 목록에 안 나오는 종류도 센다 */
export function useHasUnreadNotice() {
  return useNotifications().data?.some((n) => n.unread) ?? false
}

/** 알림 하나를 읽음으로 바꾼다. 남의 알림이거나 없으면 404(NOTIFICATION_NOT_FOUND) */
export function readNotice(id: string) {
  return customFetch<RsData<null>>(`${BASE}/${encodeURIComponent(id)}/read`, { method: 'POST' })
}
