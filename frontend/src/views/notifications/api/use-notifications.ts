'use client'

import { useQuery } from '@tanstack/react-query'

import { useAuthStatus } from '@/features/auth'
import { ApiError, customFetch, unwrapData, type RsData } from '@/shared/api'

export type NoticeCategory = 'race' | 'payment' | 'crew'

export type Notice = {
  id: string
  category: NoticeCategory
  body: string
  /** 보낸 시각 (ISO 8601) */
  sentAt: string
  /** 알림에서 갈 화면. 앱 안 주소만 버튼으로 그린다 */
  link?: { label: string; href: string }
  unread: boolean
}

/**
 * 내 알림 (GET /api/v1/notification/notifications).
 * 알림 API는 백엔드에 아직 없어 가짜 응답 모드(shared/mocks/notifications.ts)에서만 응답한다.
 * 404는 API가 아직 없는 것이라 알림이 없는 것으로 보고, 그 밖의 오류는 화면에 불러오지 못함으로 보여 준다.
 * 로그인 확인이 끝난 뒤에만 부른다.
 */
export function useNotifications() {
  const enabled = useAuthStatus() === 'authenticated'
  return useQuery({
    queryKey: ['notifications'],
    queryFn: async () => {
      try {
        const notices = unwrapData(
          await customFetch<RsData<Notice[]>>('/api/v1/notification/notifications'),
        )
        // 날짜를 읽을 수 없는 알림은 시각을 보여 줄 수 없고 순서도 정할 수 없어 뺀다
        return notices.filter((n) => !Number.isNaN(Date.parse(n.sentAt)))
      } catch (error) {
        // shortcut: 알림 API가 백엔드에 아직 없어 404를 「알림 없음」으로 본다.
        // 알림 API가 생기면 이 분기를 지운다. 남겨 두면 경로 · 라우팅이 빠진 404도 알림 없음으로 숨는다.
        if (error instanceof ApiError && error.status === 404) return []
        throw error
      }
    },
    enabled,
    retry: false,
  })
}
