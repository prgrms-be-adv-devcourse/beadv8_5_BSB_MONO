'use client'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { KeyValue, Skeleton } from '@/shared/ui'

// 피그마 v3 · 관리자 설정. 알림 스위치는 관리자 알림 정책과 API가 없어 만들지 않았다.
export function AdminSettingsPage() {
  const authenticated = useAuthStatus() === 'authenticated'
  const me = memberApi.useFindMe({ query: { select: unwrapData, enabled: authenticated } })

  return (
    <ConsolePage title="설정">
      <ConsoleCard title="내 계정" className="items-start">
        {me.data ? (
          <>
            <KeyValue label="이름" value={me.data.name ?? '-'} />
            <KeyValue label="이메일" value={me.data.email ?? '-'} />
          </>
        ) : (
          <Skeleton className="h-16 w-full" />
        )}
      </ConsoleCard>
    </ConsolePage>
  )
}
