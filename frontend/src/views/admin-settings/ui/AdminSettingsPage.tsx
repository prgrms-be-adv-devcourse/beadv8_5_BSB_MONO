'use client'

import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { formatSellerDate, modifyErrorMessage, profileSchema } from '@/entities/member'
import { ApiError, memberApi, unwrapData, type memberModel } from '@/shared/api'
import { Button, KeyValue, Skeleton, TextField, useToast } from '@/shared/ui'

// 피그마 v4 · 관리자 · 내 계정(2070:15066). 관리자 전용 설정 API가 없어 계정 관리(닉네임 · 비밀번호 재설정 메일)만 둔다.
export function AdminSettingsPage() {
  const authenticated = useAuthStatus() === 'authenticated'
  const me = memberApi.useFindMe({ query: { select: unwrapData, enabled: authenticated } })

  return (
    <ConsolePage title="내 계정">
      {me.isError ? (
        <ConsoleCard className="min-h-120 justify-center">
          <div role="alert" className="flex flex-col items-center gap-3">
            <p className="text-body1-regular text-text-secondary">계정 정보를 불러오지 못했어요</p>
            <Button variant="secondary" size="m" onClick={() => me.refetch()}>
              다시 시도하기
            </Button>
          </div>
        </ConsoleCard>
      ) : me.data ? (
        <ConsoleCard title="기본 정보" className="items-start">
          <KeyValue label="이름" value={me.data.name ?? '-'} />
          <KeyValue label="이메일" value={me.data.email ?? '-'} />
          <KeyValue label="역할" value="관리자" />
          <KeyValue
            label="가입일"
            value={me.data.createDate ? formatSellerDate(me.data.createDate) : '-'}
          />
          <h2 className="mt-3 text-title3">계정 관리</h2>
          {/* 저장 뒤 받은 닉네임으로 다시 그리도록 key를 준다 */}
          <NicknameRow key={me.data.nickname} nickname={me.data.nickname ?? ''} />
          {me.data.passwordSet && me.data.email && <PasswordRow email={me.data.email} />}
        </ConsoleCard>
      ) : (
        <ConsoleCard>
          <Skeleton className="h-16 w-full" />
        </ConsoleCard>
      )}
    </ConsolePage>
  )
}

// 검사 규칙과 문구는 회원 프로필 수정과 같은 profileSchema를 쓴다
function NicknameRow({ nickname }: { nickname: string }) {
  const queryClient = useQueryClient()
  const showToast = useToast()
  const modify = memberApi.useModifyMe()
  const [value, setValue] = useState(nickname)
  const [error, setError] = useState<string>()
  const trimmed = value.trim()

  const save = () => {
    const parsed = profileSchema.safeParse({ nickname: value })
    setError(parsed.error?.issues[0]?.message)
    if (!parsed.success) return
    modify.mutate(
      { data: parsed.data },
      {
        onSuccess: (response) => {
          // 응답이 바뀐 내 정보라 다시 부르지 않고 캐시에 넣는다(회원 프로필 수정과 같다)
          queryClient.setQueryData(memberApi.getFindMeQueryKey(), response)
          showToast({ message: '닉네임을 바꿨어요', tone: 'success' })
        },
        onError: (e) => setError(modifyErrorMessage(e).message),
      },
    )
  }

  return (
    <KeyValue
      label="닉네임"
      className="items-start [&>span:first-child]:pt-2"
      value={
        <span className="flex items-start gap-3">
          <TextField
            compact
            aria-label="닉네임"
            className="w-80"
            value={value}
            error={error}
            onChange={(event) => {
              setValue(event.target.value)
              setError(undefined)
            }}
          />
          <Button
            variant="secondary"
            size="m"
            disabled={trimmed === nickname || modify.isPending}
            loading={modify.isPending}
            onClick={save}
          >
            저장하기
          </Button>
        </span>
      }
    />
  )
}

function PasswordRow({ email }: { email: string }) {
  const showToast = useToast()
  const requestReset = memberApi.useRequestPasswordReset()
  const send = () =>
    requestReset.mutate(
      { data: { email } satisfies memberModel.PasswordResetRequestDto },
      {
        onSuccess: () => showToast({ message: `${email}로 메일을 보냈어요`, tone: 'success' }),
        onError: (e) =>
          showToast({
            message:
              (e instanceof ApiError && e.serverMessage) ||
              '메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.',
            tone: 'error',
          }),
      },
    )

  return (
    <KeyValue
      label="비밀번호"
      value={
        <Button
          variant="secondary"
          size="m"
          disabled={requestReset.isPending}
          loading={requestReset.isPending}
          onClick={send}
        >
          재설정 메일 받기
        </Button>
      }
    />
  )
}
