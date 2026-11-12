'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, FOCUS_RING, Icon, KeyValue } from '@/shared/ui'

import { maskPhone, toDotDate } from '../model/member-format'
import { EMPTY, MenuGroup, MenuRow } from './MenuRow'

/**
 * 마이 > 계정 설정 (/me/settings). 마이는 보기만 하고, 고치는 일은 여기서 시작한다.
 * 피그마 v2 · 계정 설정(1333:22497). 이메일 정보 · 비밀번호 변경 · 약관 · 고객센터 화면은 아직 없어 빼 두었다.
 * - 수정: 회원 정보 수정(/me/edit)에서 닉네임을 고친다
 * - 이름 · 생년월일 · 휴대폰은 본인인증으로 확인한 값이라 보기만 한다(휴대폰 재인증은 다음 작업)
 * - 계정 연결: 카카오 · 구글 연결과 해제(/me/accounts)
 * - 탈퇴하기: 맨 아래에 두어 실수로 누르지 않게 한다(/me/withdraw)
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
          <Avatar size="xl" name={member?.nickname} className="mb-3 size-19" />
          <p className="text-title3">
            {member?.nickname ?? EMPTY}
            <span> 님</span>
          </p>
          <p className="text-caption-regular text-text-secondary">{member?.email ?? EMPTY}</p>
        </div>

        <section
          className="mx-4 mt-6 flex flex-col gap-2 rounded-lg bg-bg-brand-subtle p-4"
          data-clarity-mask="true"
        >
          <div className="flex items-center gap-2">
            <Icon name="CircleCheck" className="size-5 shrink-0" />
            <span className="text-body2-regular">
              본인인증을 마쳤어요 · <span className="font-medium">{member?.name ?? EMPTY}</span>
            </span>
          </div>
          <KeyValue label="휴대폰" value={member?.phone ? maskPhone(member.phone) : EMPTY} />
          <KeyValue
            label="생년월일"
            value={member?.birthDate ? toDotDate(member.birthDate) : EMPTY}
          />
          {me.isError && (
            <p role="alert" className="text-caption-regular text-text-danger">
              회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}
        </section>

        <MenuGroup title="계정">
          <MenuRow href="/me/edit" icon="User" label="닉네임 변경" />
          <MenuRow href="/me/accounts" icon="Settings" label="계정 연결" />
          <MenuRow href="/me/notification-settings" icon="Bell" label="알림 설정" />
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
