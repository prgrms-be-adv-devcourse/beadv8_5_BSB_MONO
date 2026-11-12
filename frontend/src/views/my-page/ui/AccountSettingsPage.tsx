'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, buttonClassName, Divider, FOCUS_RING, Icon } from '@/shared/ui'

import { EMPTY, MenuGroup, MenuRow } from './MenuRow'
import { SocialMark } from './SocialMark'

/**
 * 마이 > 계정 설정 (/me/settings). 마이는 보기만 하고, 고치는 일은 여기서 시작한다.
 * 피그마 v2 · 계정 설정(1333:22497)과 개선 화면 모음(이메일 정보 · 약관 및 동의).
 * - 연필 · 정보 보기 · 닉네임 변경: 회원 정보 수정(/me/edit). 이름 · 생년월일 · 휴대폰은 거기서 보기만 한다
 * - 계정 연결: 카카오 · 구글 연결과 해제(/me/accounts)
 * - 탈퇴하기: 맨 아래에 두어 실수로 누르지 않게 한다(/me/withdraw)
 * TODO(api): 시안의 「비밀번호 변경」은 로그인한 회원의 비밀번호 변경 API가 없어(메일로 다시 설정하는 API뿐) 두지 않았다.
 */
export function AccountSettingsPage() {
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다. 로그아웃으로 캐시를 지운 뒤 다시 부르지도 않는다.
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const member = me.data
  return (
    <>
      <AppHeader type="sub" title="계정 설정" />
      <main className="flex flex-1 flex-col pt-(--layout-content-start) pb-6">
        {/* 회원 정보 글자는 Clarity 화면 녹화에서 가린다 */}
        <div className="flex flex-col items-center gap-1 px-4" data-clarity-mask="true">
          <div className="relative mb-3">
            <Avatar size="xl" name={member?.nickname} className="size-19" />
            <Link
              href="/me/edit"
              aria-label="회원 정보 수정"
              className={cn(
                'absolute right-0 bottom-0 flex size-6 items-center justify-center rounded-full bg-bg-brand text-text-on-brand',
                FOCUS_RING,
              )}
            >
              <svg viewBox="0 0 24 24" className="size-3.5" fill="none" aria-hidden>
                <path
                  d="M4 20h4L19 9l-4-4L4 16v4zM13 7l4 4"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </Link>
          </div>
          <p className="text-title3">
            {member?.nickname ?? EMPTY}
            <span> 님</span>
          </p>
          <p className="flex items-center gap-1.5 text-caption-regular text-text-secondary">
            {member?.email ?? EMPTY}
            {member?.socialProviders?.map((provider) => (
              <SocialMark key={provider} provider={provider} className="size-4" />
            ))}
          </p>
        </div>

        <section
          className="mx-4 mt-6 flex items-center gap-2 rounded-lg bg-bg-brand-subtle p-4"
          data-clarity-mask="true"
        >
          <Icon name="CircleCheck" className="size-5 shrink-0" />
          <span className="min-w-0 flex-1 text-body2-regular">
            본인인증을 마쳤어요 · <span className="font-medium">{member?.name ?? EMPTY}</span>
          </span>
          <Link href="/me/edit" className={buttonClassName({ size: 's' })}>
            정보 보기
          </Link>
        </section>
        {me.isError && (
          <p role="alert" className="mx-4 mt-2 text-caption-regular text-text-danger">
            회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}

        <MenuGroup title="계정">
          <MenuRow href="/me/settings/email" icon="Settings" label="이메일 정보" />
          <MenuRow href="/me/edit" icon="User" label="닉네임 변경" />
          <MenuRow href="/me/accounts" icon="Settings" label="계정 연결" />
          <MenuRow href="/me/notification-settings" icon="Bell" label="알림 설정" />
        </MenuGroup>

        <Divider />
        <MenuGroup title="고객지원">
          <MenuRow href="/me/settings/terms" icon="Orders" label="약관 및 동의" />
          <MenuRow href="/me/support" icon="Info" label="고객센터" />
        </MenuGroup>

        <Link
          href="/me/withdraw"
          className={cn(
            'mt-auto self-start rounded-sm px-4 py-3 text-caption-regular text-text-secondary',
            FOCUS_RING,
          )}
        >
          탈퇴하기
        </Link>
      </main>
    </>
  )
}
