'use client'

import { AppHeader } from '@/widgets/app-header'
import {
  SOCIAL_PROVIDERS,
  socialLinkResultMessage,
  useAuthStatus,
  useLinkSocial,
  useUnlinkSocial,
  type SocialProviderId,
} from '@/features/auth'
import { ApiError, memberApi, unwrapData } from '@/shared/api'
import { Alert, Button } from '@/shared/ui'

/**
 * 마이 > 계정 연결 (/me/accounts). 카카오 · 구글 계정을 연결하거나 해제한다.
 * 연결은 연결 표를 받은 뒤 제공자 로그인 화면을 다녀온다. 돌아오면 linked · error로 결과를 알린다.
 * 로그인할 방법이 하나만 남으면(비밀번호가 없고 소셜 연결이 하나뿐) 그 연결은 해제할 수 없다.
 */
export function AccountLinkPage({ linked, error }: { linked?: string; error?: string }) {
  const me = memberApi.useFindMe({
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const link = useLinkSocial()
  const unlink = useUnlinkSocial()
  const result = socialLinkResultMessage(linked, error)
  const connected = new Set<string>(me.data?.socialProviders ?? [])
  const lastMethod = me.data ? !me.data.passwordSet && connected.size === 1 : false
  const actionError = link.error ?? unlink.error

  return (
    <>
      <AppHeader type="sub" title="계정 연결" />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-(--layout-content-start) pb-6">
        <p className="text-body-m text-text-secondary">
          연결해 두면 이메일 대신 카카오 · 구글로 로그인할 수 있어요.
        </p>
        {result && <Alert tone={result.tone} title={result.message} />}
        {actionError && (
          <Alert
            tone="danger"
            title={
              (actionError instanceof ApiError && actionError.serverMessage) ||
              '지금은 처리할 수 없어요. 잠시 후 다시 시도해 주세요.'
            }
          />
        )}
        <section className="flex flex-col rounded-lg bg-bg-subtle px-4">
          {SOCIAL_PROVIDERS.map((provider) => {
            const isConnected = connected.has(provider.code)
            return (
              <div
                key={provider.id}
                className="flex min-h-16 items-center justify-between gap-3 border-b border-border-default py-3 last:border-b-0"
              >
                <div className="flex flex-col">
                  <span className="text-label-m">{provider.label}</span>
                  <span className="text-caption text-text-tertiary">
                    {isConnected ? '연결됨' : '연결 안 됨'}
                  </span>
                </div>
                {isConnected ? (
                  <Button
                    variant="secondary"
                    size="s"
                    disabled={!me.data || lastMethod || unlink.isPending}
                    onClick={() => unlink.mutate(provider.id as SocialProviderId)}
                  >
                    해제
                  </Button>
                ) : (
                  <Button
                    size="s"
                    disabled={!me.data || link.isPending}
                    onClick={() => link.mutate(provider.id as SocialProviderId)}
                  >
                    연결
                  </Button>
                )}
              </div>
            )
          })}
        </section>
        {lastMethod && (
          <p className="text-caption text-text-tertiary">
            로그인할 방법이 하나뿐이라 해제할 수 없어요. 다른 계정을 먼저 연결해 주세요.
          </p>
        )}
      </main>
    </>
  )
}
