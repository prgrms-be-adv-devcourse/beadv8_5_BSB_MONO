'use client'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { Button, KeyValue, Skeleton } from '@/shared/ui'

// 피그마 v3 · 관리자 설정. 알림 스위치는 관리자 알림 정책과 API가 없어 만들지 않았다.
export function AdminSettingsPage() {
  const authenticated = useAuthStatus() === 'authenticated'
  const me = memberApi.useFindMe({ query: { select: unwrapData, enabled: authenticated } })

  return (
    <ConsolePage title="설정">
      <ConsoleCard
        title={me.isError ? undefined : '내 계정'}
        className={me.isError ? 'min-h-120 justify-center' : 'items-start'}
      >
        {me.isError ? (
          <div role="alert" className="flex flex-col items-center gap-3">
            <p className="text-body1-regular text-text-secondary">설정을 불러오지 못했어요</p>
            <Button variant="secondary" size="m" onClick={() => me.refetch()}>
              다시 시도하기
            </Button>
          </div>
        ) : me.data ? (
          <>
            <KeyValue label="이름" value={me.data.name ?? '-'} />
            <KeyValue label="이메일" value={me.data.email ?? '-'} />
            <KeyValue label="역할" value="관리자" />
          </>
        ) : (
          <Skeleton className="h-16 w-full" />
        )}
      </ConsoleCard>
    </ConsolePage>
  )
}
