'use client'

import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { cn } from '@/shared/lib'
import { buttonClassName, FOCUS_RING, KeyValue } from '@/shared/ui'

import { maskPhone, toDotDate } from '../model/member-format'
import { EMPTY, MenuCard, MenuRow } from './MenuRow'

/**
 * 마이 > 계정 설정 (/me/settings). 마이는 보기만 하고, 고치는 일은 여기서 시작한다.
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
      <main className="flex flex-1 flex-col gap-4 px-4 pt-(--layout-content-start) pb-6">
        <section className="flex flex-col gap-2 rounded-lg bg-bg-subtle p-4">
          <h2 className="pb-1 text-label-strong">회원 정보</h2>
          <div className="flex items-center justify-between gap-4 text-body-s">
            <span className="shrink-0 text-text-tertiary">닉네임</span>
            <span className="flex min-w-0 items-center gap-2">
              <span className="truncate font-medium text-text-primary">
                {member?.nickname ?? EMPTY}
              </span>
              <Link
                href="/me/edit"
                aria-label="닉네임 수정"
                className={buttonClassName({ variant: 'secondary', size: 's' })}
              >
                수정
              </Link>
            </span>
          </div>
          <KeyValue label="이메일" value={member?.email ?? EMPTY} />
        </section>

        <section className="flex flex-col gap-2 rounded-lg bg-bg-subtle p-4">
          <h2 className="pb-1 text-label-strong">본인인증 정보</h2>
          <KeyValue label="이름" value={member?.name ?? EMPTY} />
          <KeyValue label="휴대폰" value={member?.phone ? maskPhone(member.phone) : EMPTY} />
          <KeyValue
            label="생년월일"
            value={member?.birthDate ? toDotDate(member.birthDate) : EMPTY}
          />
          <p className="text-caption text-text-secondary">
            본인인증으로 확인한 정보라 바꿀 수 없어요
          </p>
          {me.isError && (
            <p role="alert" className="text-caption text-text-danger">
              회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          )}
        </section>

        <MenuCard>
          <MenuRow
            href="/me/accounts"
            icon="User"
            label="계정 연결"
            description="카카오 · 구글로 로그인할 수 있게 연결해요"
          />
        </MenuCard>

        <Link
          href="/me/withdraw"
          className={cn(
            'mt-auto self-center rounded-sm px-2 py-3 text-label-s text-text-tertiary underline',
            FOCUS_RING,
          )}
        >
          탈퇴하기
        </Link>
      </main>
    </>
  )
}
