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
// 보내기가 시간 초과로 끝나면 메시지가 이미 갔을 수 있다 (백엔드는 링크를 취소하지 않는다)
const SEND_UNCONFIRMED = `카카오톡이 응답하지 않아 보냈는지 확인하지 못했어요. 받았는지 확인한 뒤 다시 보내거나 ${COPY_LINK_HINT}`
const PARTIAL_UNCONFIRMED =
  '카카오톡이 응답하지 않아 보냈는지 확인하지 못한 사람이 있어요. 받았는지 확인한 뒤 다시 보내 주세요.'

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
  // 이미 보낸 친구. 일부만 보내진 뒤 다시 고르지 않게 목록에서 뺀다
  const [sentUuids, setSentUuids] = useState<string[]>([])
  const [partial, setPartial] = useState<PartialResult | null>(null)
  // 목록 위에 띄우는 보내기 실패 안내. 목록과 보내기 버튼은 남겨 다시 보낼 수 있게 한다
  const [sendError, setSendError] = useState<string | null>(null)
  // 동의 필요·카카오 미연결이면 목록 대신 안내만 보여 준다
  const [blockingCode, setBlockingCode] = useState<string | null>(null)
  const friends = memberApi.useFindKakaoInviteFriends(crewId, {
    query: { select: unwrapData, enabled: open, retry: false },
  })
  const send = memberApi.useSendKakaoCrewInvites()
  const consent = useMutation({
    mutationFn: async () => {
      await memberApi.issueKakaoMessageConsentTicket()
      goToSocialLogin(socialAuthorizeHref('kakao', { next: `/crews/${crewId}/invites` }))
    },
    onError: (error) => {
      if (codeOf(error) === 'KAKAO_NOT_LINKED') setBlockingCode('KAKAO_NOT_LINKED')
      else showToast({ tone: 'error', message: '잠시 후 다시 시도해 주세요.' })
    },
  })

  const nameOf = (uuid: string) =>
    friends.data?.find((friend) => friend.uuid === uuid)?.nickname ?? '카카오 친구'

  const close = (next: boolean) => {
    // 보내는 중에 닫으면 결과를 알 수 없어 같은 친구에게 또 보내게 되므로 끝날 때까지 닫지 않는다
    if (!next && send.isPending) return
    if (!next) {
      setSelected([])
      setSentUuids([])
      setPartial(null)
      setSendError(null)
      setBlockingCode(null)
      send.reset()
    }
    onOpenChange(next)
  }

  const toggle = (uuid: string, checked: boolean) =>
    setSelected((current) => (checked ? [...current, uuid] : current.filter((u) => u !== uuid)))

  const submit = () => {
    setSendError(null)
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
          setSentUuids((current) => [...current, ...result.sentUuids])
          // 하루 한도로 못 보낸 친구는 오늘 다시 보내도 실패하고, 보냈는지 모르는 친구는 두 번 받을 수 있어 고른 상태로 남기지 않는다
          setSelected(
            result.failures
              .filter((failure) => failure.reason === 'KAKAO_ERROR')
              .map((failure) => failure.uuid),
          )
          setPartial({
            sent: result.sentUuids.length,
            failedNames: result.failures.map((failure) => nameOf(failure.uuid)),
            dailyLimit: result.failures.some((failure) => failure.reason === 'DAILY_LIMIT'),
            unconfirmed: result.failures.some((failure) => failure.reason === 'UNCONFIRMED'),
          })
        },
        onError: (error) => {
          const code = codeOf(error)
          if (code === 'KAKAO_MESSAGE_CONSENT_REQUIRED' || code === 'KAKAO_NOT_LINKED') {
            setBlockingCode(code)
          } else {
            setSendError(code === 'KAKAO_UNAVAILABLE' ? SEND_UNCONFIRMED : problemMessage(error))
          }
        },
      },
    )
  }

  const friendsCode = friends.isError ? codeOf(friends.error) : null
  const blocking =
    blockingCode ??
    (friendsCode === 'KAKAO_MESSAGE_CONSENT_REQUIRED' || friendsCode === 'KAKAO_NOT_LINKED'
      ? friendsCode
      : null)
  const list = friends.data?.filter((friend) => !sentUuids.includes(friend.uuid))
  const count = selected.length

  return (
    <BottomSheet
      open={open}
      onOpenChange={close}
      title="카카오톡 친구에게 보내기"
      footer={
        blocking === null && list && list.length > 0 ? (
          <Button size="l" disabled={count === 0} loading={send.isPending} onClick={submit}>
            {count === 0 ? '보내기' : `${count}명에게 보내기`}
          </Button>
        ) : undefined
      }
    >
      <div className="flex flex-col gap-4 px-2 pb-2">
        {blocking === 'KAKAO_MESSAGE_CONSENT_REQUIRED' ? (
          <>
            <p className="text-body-m">
              카카오톡으로 초대를 보내려면 친구 목록과 메시지 보내기 동의가 필요해요.
            </p>
            <Button size="l" loading={consent.isPending} onClick={() => consent.mutate()}>
              동의하러 가기
            </Button>
          </>
        ) : blocking === 'KAKAO_NOT_LINKED' ? (
          <>
            <p className="text-body-m">마이 &gt; 계정 연결에서 카카오를 먼저 연결해 주세요.</p>
            <Link
              href="/me/accounts"
              className={buttonClassName({ variant: 'secondary', size: 'l' })}
            >
              계정 연결로 가기
            </Link>
          </>
        ) : friends.isError ? (
          <>
            <Alert tone="danger" title={problemMessage(friends.error)} />
            <Button variant="secondary" size="m" onClick={() => friends.refetch()}>
              다시 불러오기
            </Button>
          </>
        ) : list === undefined ? (
          <FriendsSkeleton />
        ) : (
          <>
            {sendError && <Alert tone="danger" title={sendError} />}
            {partial && (
              <Alert
                tone="warning"
                title={`${partial.sent}명에게 보냈어요. ${partial.failedNames.length}명에게는 보내지 못했어요.`}
                description={`보내지 못한 사람: ${partial.failedNames.join(', ')}`}
              />
            )}
            {partial?.dailyLimit && <Alert tone="warning" title={DAILY_LIMIT} />}
            {partial?.unconfirmed && <Alert tone="warning" title={PARTIAL_UNCONFIRMED} />}
            {list.length === 0 ? (
              <p className="text-body-m text-text-secondary">
                {partial
                  ? `더 보낼 수 있는 친구가 없어요. ${COPY_LINK_HINT}`
                  : `크루런을 쓰는 카카오 친구가 없어요. ${COPY_LINK_HINT}`}
              </p>
            ) : (
              <>
                <p className="text-caption text-text-secondary">
                  {MAX_RECEIVERS}명까지 고를 수 있어요
                </p>
                <ul className="flex flex-col gap-3">
                  {list.map((friend) => (
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
          </>
        )}
      </div>
    </BottomSheet>
  )
}

type PartialResult = {
  sent: number
  failedNames: string[]
  dailyLimit: boolean
  unconfirmed: boolean
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
      {/* 이름은 체크박스가 읽으므로 사진은 읽지 않는다 */}
      <span aria-hidden>
        <Avatar size="l" src={friend.profileImageUrl ?? undefined} name={name} />
      </span>
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

// 하루 한도·카카오 장애는 링크 복사로 안내하고, 카카오와 관계없는 거절은 서버 문구를 보여 준다
function problemMessage(error: unknown): string {
  const code = codeOf(error)
  if (code === 'KAKAO_MESSAGE_DAILY_LIMIT') return DAILY_LIMIT
  if (code === 'KAKAO_UNAVAILABLE') return KAKAO_DOWN
  return (error instanceof ApiError && error.serverMessage) || '잠시 후 다시 시도해 주세요.'
}
