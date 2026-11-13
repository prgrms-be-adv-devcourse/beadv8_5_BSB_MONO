'use client'

import { useEffect, useRef, useState, useSyncExternalStore } from 'react'

import { AppHeader } from '@/widgets/app-header'
import {
  forgetLinkingProvider,
  linkingProvider,
  SOCIAL_PROVIDERS,
  socialLinkResultMessage,
  socialProviderOf,
  useAuthStatus,
  useLinkSocial,
  useUnlinkSocial,
  type SocialProviderId,
} from '@/features/auth'
import { ApiError, memberApi, unwrapData } from '@/shared/api'
import { Badge, Button, Dialog, Icon, useToast } from '@/shared/ui'

import { SocialMark } from './SocialMark'

// 화면에는 카카오 · Google로 쓴다(로그인 화면의 「구글」 표기와 다르다)
const LABEL: Record<SocialProviderId, string> = { kakao: '카카오', google: 'Google' }

/**
 * 마이 > 계정 연결 (/me/accounts). 카카오 · Google 계정을 연결하거나 해제한다.
 * 피그마 M30-L 기본(1333:21500) · 해제할 수 없음(1333:21538) · 해제 확인(1333:21576) · 다른 회원에 연결된 계정(1333:21617)
 * · 연결 실패하거나 취소함(1333:21658) · 해제 뒤(1333:22819) · 연결 성공(1333:22858).
 * 연결은 연결 표를 받은 뒤 제공자 로그인 화면을 다녀온다. 돌아오면 linked · error로 결과를 알린다.
 * 로그인할 방법이 하나만 남으면(비밀번호가 없고 소셜 연결이 하나뿐) 그 연결은 해제할 수 없다.
 */
export function AccountLinkPage({ linked, error }: { linked?: string; error?: string }) {
  const me = memberApi.useFindMe({
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const link = useLinkSocial()
  const unlink = useUnlinkSocial()
  const toast = useToast()
  const [unlinking, setUnlinking] = useState<SocialProviderId>()
  const [errorOpen, setErrorOpen] = useState(Boolean(error))
  const connected = new Set<string>(me.data?.socialProviders ?? [])
  const lastMethod = me.data ? !me.data.passwordSet && connected.size === 1 : false

  // 연결을 마치고 돌아오면 한 번만 알린다
  const notified = useRef(false)
  useEffect(() => {
    const provider = socialProviderOf(linked)
    if (!provider || notified.current) return
    notified.current = true
    forgetLinkingProvider()
    toast({ message: `${LABEL[provider.id]} 계정을 연결했어요`, tone: 'success' })
  }, [linked, toast])

  // 돌아온 주소에는 어느 제공자인지 없다. 시작할 때 남겨 둔 제공자를 쓰고, 없으면(저장소를 못 쓴 경우)
  // 연결 안 된 제공자가 하나뿐일 때만 그것을 시도한 것으로 본다.
  const started = useSyncExternalStore(
    () => () => {},
    linkingProvider,
    () => undefined,
  )
  const unconnected = SOCIAL_PROVIDERS.filter((p) => !connected.has(p.code))
  const guessed = unconnected.length === 1 && me.data ? unconnected[0].id : undefined
  const triedId = started ?? guessed
  const tried = SOCIAL_PROVIDERS.find((p) => p.id === triedId)
  const takenByOther = error === 'AUTH_SOCIAL_ALREADY_LINKED'
  // 이미 연결한 제공자를 또 연결하려 한 경우는 다시 시도해도 같다
  const alreadyMine = error === 'MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED'
  const actionError = link.error ?? unlink.error

  return (
    <>
      <AppHeader type="sub" title="계정 연결" />
      <main className="flex flex-1 flex-col pt-(--layout-content-start) pb-6">
        <h2 className="px-4 pb-2 text-title3">로그인 방법</h2>
        <ul className="flex flex-col px-4">
          <li className="flex min-h-16 items-center gap-3 py-3">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-bg-subtle text-text-tertiary inset-ring inset-ring-border-default">
              <Icon name="User" className="size-4" />
            </span>
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <span className="flex items-center gap-2 text-body1-bold">
                이메일
                {me.data?.passwordSet && <Badge>사용 중</Badge>}
              </span>
              {me.data?.passwordSet && (
                <span
                  className="truncate text-caption-regular text-text-secondary"
                  data-clarity-mask="true"
                >
                  {me.data.email} · 비밀번호로 로그인
                </span>
              )}
            </div>
          </li>
          {SOCIAL_PROVIDERS.map((provider) => {
            const isConnected = connected.has(provider.code)
            return (
              <li key={provider.id} className="flex min-h-16 items-center gap-3 py-3">
                <SocialMark provider={provider.code} className="size-8" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="flex items-center gap-2 text-body1-bold">
                    {LABEL[provider.id]}
                    {isConnected && <Badge>연결됨</Badge>}
                  </span>
                  {isConnected && lastMethod && (
                    <span className="text-caption-regular text-text-secondary">
                      {LABEL[provider.id]}로 가입했어요
                    </span>
                  )}
                </div>
                {isConnected ? (
                  <Button
                    variant="secondary"
                    size="s"
                    disabled={!me.data || lastMethod || unlink.isPending}
                    onClick={() => setUnlinking(provider.id)}
                  >
                    연결 해제
                  </Button>
                ) : (
                  <Button
                    size="s"
                    disabled={!me.data || link.isPending}
                    onClick={() => link.mutate(provider.id)}
                  >
                    연결하기
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
        {actionError && (
          <p role="alert" className="px-4 pt-3 text-caption-regular text-text-danger">
            {(actionError instanceof ApiError && actionError.serverMessage) ||
              '지금은 처리할 수 없어요. 잠시 후 다시 시도해 주세요.'}
          </p>
        )}
      </main>

      <Dialog
        open={unlinking !== undefined}
        onOpenChange={(open) => !open && setUnlinking(undefined)}
        type="danger"
        title={`${unlinking ? LABEL[unlinking] : ''} 연결을 해제할까요?`}
        body={`해제하면 ${unlinking ? LABEL[unlinking] : ''}로 로그인할 수 없어요. 앞으로는 이메일로 로그인해 주세요.`}
        cancelLabel="돌아가기"
        confirmLabel="연결 해제"
        confirming={unlink.isPending}
        onConfirm={() => {
          const provider = unlinking
          if (!provider) return
          unlink.mutate(provider, {
            onSuccess: () => toast({ message: `${LABEL[provider]} 연결을 해제했어요` }),
            onSettled: () => setUnlinking(undefined),
          })
        }}
      />
      {alreadyMine ? (
        <Dialog
          open={errorOpen}
          onOpenChange={setErrorOpen}
          type="alert"
          title={socialLinkResultMessage(undefined, error)?.message ?? ''}
          body="로그인 방법 목록에서 연결 상태를 확인해 주세요."
          confirmLabel="확인"
          onConfirm={() => setErrorOpen(false)}
        />
      ) : takenByOther ? (
        <Dialog
          open={errorOpen}
          onOpenChange={setErrorOpen}
          type="alert"
          title={`이 ${tried ? LABEL[tried.id] : '소셜'} 계정은 다른 크루런 회원에 연결돼 있어요`}
          body={`그 회원으로 로그인해 연결을 해제하거나, 다른 ${tried ? LABEL[tried.id] : '소셜'} 계정을 연결해 주세요.`}
          confirmLabel="확인"
          onConfirm={() => setErrorOpen(false)}
        />
      ) : (
        <Dialog
          open={errorOpen}
          onOpenChange={setErrorOpen}
          title={`${tried ? LABEL[tried.id] : '소셜'} 연결을 마치지 못했어요`}
          body={`동의 화면에서 취소했거나 ${tried ? LABEL[tried.id] : '소셜 계정'}과 연결하지 못했어요. 다시 시도해 주세요.`}
          cancelLabel="닫기"
          confirmLabel="다시 시도하기"
          onConfirm={() => {
            setErrorOpen(false)
            if (tried) link.mutate(tried.id)
          }}
        />
      )}
    </>
  )
}
