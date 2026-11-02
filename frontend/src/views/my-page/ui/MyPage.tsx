'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { useAuthStatus, useLogout } from '@/features/auth'
import { isNoApplication } from '@/entities/member'
import { myTicketCountQueryOptions } from '@/entities/ticket'
import { useMyBalance } from '@/entities/wallet'
import { memberApi, unwrapData } from '@/shared/api'
import { cn, formatWon } from '@/shared/lib'
import { buttonClassName, FOCUS_RING, Icon, KeyValue, Switch, type IconName } from '@/shared/ui'

import { maskPhone, toDotDate } from '../model/member-format'

// 피그마 V2 · M30 마이 · 설정 (+ M30-2 판매자 승인 후)
// 회원 정보는 내 정보 조회(GET /api/v1/member/members/me)로 불러온다.
// 예치금은 지갑 잔액 API(useMyBalance)로 불러온다.
// 참가 대회 수는 내 참가권 화면과 같은 목록(주문 API)에서 취소한 참가권만 빼고 센다.
// 판매자 메뉴는 내 판매자 신청 조회(GET /api/v1/member/sellers/me) 상태로 갈 곳을 정한다(SellerMenuRow).
// TODO(api): 알림 설정 API가 아직 없다. 지금은 시안 값을 보여 준다.
export function MyPage() {
  const logout = useLogout()
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다. 로그아웃으로 캐시를 지운 뒤 다시 부르지도 않는다.
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated' },
  })
  const member = me.data
  const { balance } = useMyBalance()
  const ticketCount = useQuery(myTicketCountQueryOptions(useAuthStatus() === 'authenticated'))
  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-(--layout-content-start) pb-6">
        <div className="flex items-center gap-3">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-full bg-bg-brand-subtle text-label-m text-text-brand"
          >
            {member?.nickname?.slice(0, 1)}
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <p className="text-heading-m">{member?.nickname ?? EMPTY}</p>
            <p className="text-body-s text-text-secondary">{member?.email ?? EMPTY}</p>
          </div>
          <Link href="/me/edit" className={buttonClassName({ variant: 'secondary', size: 's' })}>
            닉네임 수정
          </Link>
        </div>

        <section className="flex flex-col gap-2 rounded-lg bg-bg-subtle p-4">
          <KeyValue label="이름" value={member?.name ?? EMPTY} />
          <KeyValue label="휴대폰" value={member?.phone ? maskPhone(member.phone) : EMPTY} />
          <KeyValue
            label="생년월일"
            value={member?.birthDate ? toDotDate(member.birthDate) : EMPTY}
          />
          {me.isError ? (
            <p role="alert" className="text-caption text-text-danger">
              회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
            </p>
          ) : (
            <p className="text-caption text-text-secondary">
              가입할 때 직접 입력한 정보예요. 바뀌면 직접 고칠 수 있어요.
            </p>
          )}
          <Link
            href="/me/edit"
            className={buttonClassName({
              variant: 'secondary',
              size: 's',
              className: 'self-start',
            })}
          >
            정보 수정
          </Link>
        </section>

        <MenuCard>
          <MenuRow
            href="/tickets"
            icon="Calendar"
            label="내 참가 대회"
            value={ticketCount.data === undefined ? '-' : `${ticketCount.data}개`}
          />
          <MenuRow
            href="/me/deposit"
            icon="Package"
            label="예치금"
            value={balance === undefined ? '-' : formatWon(balance)}
          />
          {/* 크루는 하단 탭이 아니라 마이의 이 메뉴로 들어간다(시안v4 M09-0) */}
          <MenuRow href="/crews" icon="Users" label="내 크루" />
        </MenuCard>

        <h2 className="pt-2 text-label-strong">알림 설정</h2>
        <section className="flex flex-col gap-4 rounded-lg bg-bg-surface px-4 py-4 shadow-sm inset-ring inset-ring-border-default">
          <SwitchRow
            label="마케팅 정보 받기"
            description="2025.3.2 동의 · 2년마다 다시 여쭤봐요"
            defaultChecked
          />
          <SwitchRow label="밤 9시~아침 8시 광고 받기" description="마케팅 동의와 따로 받아요" />
        </section>

        <MenuCard>
          <SellerMenuRow />
        </MenuCard>

        <section className="flex flex-col rounded-lg bg-bg-subtle px-4 pt-4 pb-2">
          <h2 className="pb-2 text-label-strong">계정 관리</h2>
          <MenuRow
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            icon="LogOut"
            label="로그아웃"
          />
          <MenuRow
            href="/me/withdraw"
            icon="Settings"
            label="계정 설정"
            description="계정과 개인정보 설정을 관리할 수 있어요"
          />
        </section>
      </main>
      <BottomNav />
    </>
  )
}

/** 아직 불러오지 못한 값 자리 */
const EMPTY = '-'

function MenuCard({ children }: { children: ReactNode }) {
  return (
    <section className="flex flex-col rounded-lg bg-bg-surface px-4 py-2 shadow-sm inset-ring inset-ring-border-default">
      {children}
    </section>
  )
}

// 피그마 M30(신청 전 · 심사 중 · 반려) / M30-2(승인 후)의 판매자 줄.
// - 승인: "판매자 센터". 결과를 아직 안 봤으면(resultCheckedAt 없음) 결과 화면을 한 번 거친다.
// - 심사 중 · 반려: 결과 화면 / 신청 전(404): 신청 화면
// - 불러오는 중 · 오류: 줄은 그대로 두고 결과 화면으로 보낸다(결과 화면이 상태를 다시 안내한다).
function SellerMenuRow() {
  const mine = memberApi.useFindMine({
    // 신청 전 404는 다시 불러도 같아서 재시도하지 않는다
    query: { select: unwrapData, enabled: useAuthStatus() === 'authenticated', retry: false },
  })
  if (mine.data?.status === 'APPROVED') {
    // 서버가 아직 resultCheckedAt을 주지 않으면(undefined) 안 본 것으로 친다
    return (
      <MenuRow
        href={mine.data.resultCheckedAt ? '/seller' : '/seller-apply/result'}
        icon="Dashboard"
        label="판매자 센터"
        description="대회 등록 · 판매 현황 · 정산을 관리해요"
      />
    )
  }
  return (
    <MenuRow
      href={mine.isError && isNoApplication(mine.error) ? '/seller-apply' : '/seller-apply/result'}
      icon="Dashboard"
      label="판매자 등록 신청"
      description="대회를 열고 참가권을 팔 수 있어요"
    />
  )
}

type MenuRowProps = {
  icon: IconName
  label: string
  value?: string
  description?: string
} & ({ href: Route } | { onClick: () => void; disabled?: boolean })

// 다른 화면으로 가는 줄은 링크, 그 자리에서 실행하는 줄(로그아웃)은 같은 모양의 버튼
function MenuRow({ icon, label, value, description, ...action }: MenuRowProps) {
  const content = (
    <>
      <Icon name={icon} className="size-5" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-label-m">{label}</span>
        {description && <span className="text-caption text-text-secondary">{description}</span>}
      </span>
      {value && <span className="text-label-m">{value}</span>}
      <Icon name="ChevronRight" className="size-5" />
    </>
  )
  const className = cn('flex items-center gap-3 rounded-md py-3', FOCUS_RING)
  if ('href' in action) {
    return (
      <Link href={action.href} className={className}>
        {content}
      </Link>
    )
  }
  return (
    <button
      type="button"
      onClick={action.onClick}
      disabled={action.disabled}
      className={cn(className, 'w-full text-left')}
    >
      {content}
    </button>
  )
}

function SwitchRow({
  label,
  description,
  defaultChecked,
}: {
  label: string
  description: string
  defaultChecked?: boolean
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <div className="flex flex-col gap-0.5">
        <span className="text-label-m">{label}</span>
        <span className="text-caption text-text-secondary">{description}</span>
      </div>
      <Switch aria-label={label} defaultChecked={defaultChecked} />
    </div>
  )
}
