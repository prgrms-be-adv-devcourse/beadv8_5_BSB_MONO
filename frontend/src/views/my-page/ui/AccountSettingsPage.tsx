'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { Avatar, buttonClassName, Divider, Icon } from '@/shared/ui'

import { EMPTY, MenuGroup, MenuRow } from './MenuRow'
import { SocialMark } from './SocialMark'

/**
 * 마이 > 계정 설정 (/me/settings). 마이는 보기만 하고, 고치는 일은 여기서 시작한다.
 * 피그마 v2 · 계정 설정(1333:22497)과 개선 화면 모음(이메일 정보 · 약관 및 동의).
 * - 정보 보기: 회원 정보 수정(/me/edit). 이름 · 생년월일 · 휴대폰은 거기서 보기만 한다. 닉네임은 마이의 프로필 줄에서도 고친다
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
      <main className="flex flex-1 flex-col pb-6">
        {/* 회원 정보 글자는 Clarity 화면 녹화에서 가린다 */}
        <div className="flex flex-col items-center gap-1 px-4 pt-6" data-clarity-mask="true">
          <Avatar size="xl" name={member?.nickname} className="mb-3 size-19" />
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

        <section className="mx-4 mt-4 mb-4 flex items-center gap-2 p-4" data-clarity-mask="true">
          <Icon name="CircleCheck" className="size-5 shrink-0" />
          <span className="min-w-0 flex-1 text-body1-regular">
            본인인증을 마쳤어요 · <span className="font-medium">{member?.name ?? EMPTY}</span>
          </span>
          <Link href="/me/edit" className={buttonClassName({ variant: 'ghost', size: 's' })}>
            정보 보기
          </Link>
        </section>
        {me.isError && (
          <p role="alert" className="mx-4 mb-2 text-caption-regular text-text-danger">
            회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}

        <Divider type="section" />
        <MenuGroup title="계정" className="pt-6">
          <MenuRow tall href="/me/settings/email" icon="Settings" label="이메일 정보" />
          <MenuRow tall href="/me/accounts" icon="Settings" label="계정 연결" />
          <MenuRow tall href="/me/notification-settings" icon="Bell" label="알림 설정" />
        </MenuGroup>

        <MenuGroup title="고객지원">
          <MenuRow tall href="/me/settings/terms" label="약관 및 동의" />
          <MenuRow tall href="/me/support" label="고객센터" />
        </MenuGroup>

        <Link
          href="/me/withdraw"
          className={buttonClassName({
            variant: 'ghost',
            className: 'mx-4 mt-4 self-start text-text-danger',
          })}
        >
          탈퇴하기
        </Link>
      </main>
    </>
  )
}
