'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { goToSocialLogin, socialAuthorizeHref } from '@/features/auth'
import { ApiError, memberApi, memberModel, unwrapData } from '@/shared/api'
import {
  Alert,
  Avatar,
  BottomSheet,
  Button,
  buttonClassName,
  Checkbox,
  Skeleton,
  useToast,
} from '@/shared/ui'

/** 카카오가 한 번에 받는 사람 수 */
const MAX_RECEIVERS = 5

const COPY_LINK_HINT = '초대 링크를 복사해 보내 주세요.'
const DAILY_LIMIT = `오늘은 카카오톡으로 더 보낼 수 없어요. ${COPY_LINK_HINT}`
const KAKAO_DOWN = `카카오톡이 응답하지 않아요. 잠시 뒤 다시 보내거나 ${COPY_LINK_HINT}`

// 크루 초대 카카오톡 보내기 (결정 kakao-crew-invite 4). 크루런을 쓰는 카카오 친구를 5명까지 골라 보낸다.
// 메시지 동의가 없으면 동의 표를 받은 뒤 카카오 추가 동의 화면으로 옮기고, 끝나면 이 화면으로 돌아온다.
export function KakaoInviteSheet({
  crewId,
  open,
  onOpenChange,
}: {
  crewId: number
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const showToast = useToast()
  const [selected, setSelected] = useState<string[]>([])
  const [sendProblem, setSendProblem] = useState<string | null>(null)
  const [partial, setPartial] = useState<{ sent: number; failedNames: string[] } | null>(null)
  const friends = memberApi.useFindKakaoInviteFriends(crewId, {
    query: { select: unwrapData, enabled: open, retry: false },
  })
  const send = memberApi.useSendKakaoCrewInvites()
  const consent = useMutation({
    mutationFn: async () => {
      await memberApi.issueKakaoMessageConsentTicket()
      goToSocialLogin(socialAuthorizeHref('kakao', { next: `/crews/${crewId}/invites` }))
    },
    onError: () => showToast({ tone: 'error', message: '잠시 후 다시 시도해 주세요.' }),
  })

  const nameOf = (uuid: string) =>
    friends.data?.find((friend) => friend.uuid === uuid)?.nickname ?? '카카오 친구'

  const close = (next: boolean) => {
    if (!next) {
      setSelected([])
      setSendProblem(null)
      setPartial(null)
      send.reset()
    }
    onOpenChange(next)
  }

  const toggle = (uuid: string, checked: boolean) =>
    setSelected((current) => (checked ? [...current, uuid] : current.filter((u) => u !== uuid)))

  const submit = () => {
    setSendProblem(null)
    setPartial(null)
    send.mutate(
      { crewId, data: { receiverUuids: selected } },
      {
        onSuccess: (response) => {
          void queryClient.invalidateQueries({
            queryKey: memberApi.getFindCrewInvitesQueryKey(crewId),
          })
          const result = unwrapData(response)
          if (result.failures.length === 0) {
            showToast({
              tone: 'success',
              message: `${result.sentUuids.length}명에게 초대를 보냈어요`,
            })
            close(false)
            return
          }
          setSelected([])
          setPartial({
            sent: result.sentUuids.length,
            failedNames: result.failures.map((failure) => nameOf(failure.uuid)),
          })
        },
        onError: (error) => setSendProblem(codeOf(error)),
      },
    )
  }

  const problem = sendProblem ?? (friends.isError ? codeOf(friends.error) : null)
  const count = selected.length

  return (
    <BottomSheet
      open={open}
      onOpenChange={close}
      title="카카오톡으로 보내기"
      footer={
        problem === null && friends.data && friends.data.length > 0 ? (
          <Button size="l" disabled={count === 0} loading={send.isPending} onClick={submit}>
            {count === 0 ? '보내기' : `${count}명에게 보내기`}
          </Button>
        ) : undefined
      }
    >
      <div className="flex flex-col gap-4 px-2 pb-2">
        {problem === 'KAKAO_MESSAGE_CONSENT_REQUIRED' ? (
          <>
            <p className="text-body-m">
              카카오톡으로 초대를 보내려면 친구 목록과 메시지 보내기 동의가 필요해요.
            </p>
            <Button size="l" loading={consent.isPending} onClick={() => consent.mutate()}>
              동의하러 가기
            </Button>
          </>
        ) : problem === 'KAKAO_NOT_LINKED' ? (
          <>
            <p className="text-body-m">마이 &gt; 계정 연결에서 카카오를 먼저 연결해 주세요.</p>
            <Link
              href="/me/accounts"
              className={buttonClassName({ variant: 'secondary', size: 'l' })}
            >
              계정 연결로 가기
            </Link>
          </>
        ) : problem === 'KAKAO_MESSAGE_DAILY_LIMIT' ? (
          <Alert tone="warning" title={DAILY_LIMIT} />
        ) : problem !== null ? (
          <>
            <Alert tone="danger" title={KAKAO_DOWN} />
            {sendProblem === null && (
              <Button variant="secondary" size="m" onClick={() => friends.refetch()}>
                다시 불러오기
              </Button>
            )}
          </>
        ) : friends.data === undefined ? (
          <FriendsSkeleton />
        ) : friends.data.length === 0 ? (
          <p className="text-body-m text-text-secondary">
            크루런을 쓰는 카카오 친구가 없어요. {COPY_LINK_HINT}
          </p>
        ) : (
          <>
            {partial && (
              <Alert
                tone="warning"
                title={`${partial.sent}명에게 보냈어요. ${partial.failedNames.length}명에게는 보내지 못했어요.`}
                description={`보내지 못한 사람: ${partial.failedNames.join(', ')}`}
              />
            )}
            <p className="text-caption text-text-secondary">{MAX_RECEIVERS}명까지 고를 수 있어요</p>
            <ul className="flex flex-col gap-3">
              {friends.data.map((friend) => (
                <li key={friend.uuid}>
                  <FriendCheckbox
                    friend={friend}
                    checked={selected.includes(friend.uuid)}
                    disabled={!selected.includes(friend.uuid) && count >= MAX_RECEIVERS}
                    onChange={(checked) => toggle(friend.uuid, checked)}
                  />
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </BottomSheet>
  )
}

function FriendCheckbox({
  friend,
  checked,
  disabled,
  onChange,
}: {
  friend: memberModel.KakaoFriendDto
  checked: boolean
  disabled: boolean
  onChange: (checked: boolean) => void
}) {
  const name = friend.nickname ?? '카카오 친구'
  return (
    <div className="flex items-center gap-3">
      <Avatar size="l" src={friend.profileImageUrl ?? undefined} name={name} />
      <Checkbox
        className="flex-1 flex-row-reverse justify-between"
        checked={checked}
        disabled={disabled}
        onCheckedChange={onChange}
        label={name}
      />
    </div>
  )
}

function FriendsSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <span role="status" className="sr-only">
        카카오 친구를 불러오는 중
      </span>
      <Skeleton className="w-2/3" />
      <Skeleton className="w-1/2" />
    </div>
  )
}

function codeOf(error: unknown): string {
  return (error instanceof ApiError && error.code) || 'UNKNOWN'
}
