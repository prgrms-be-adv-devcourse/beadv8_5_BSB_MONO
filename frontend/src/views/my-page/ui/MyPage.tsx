'use client'

import { useQuery } from '@tanstack/react-query'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { useAuthStatus, useLogout } from '@/features/auth'
import { isNoApplication } from '@/entities/member'
import { myTicketCountQueryOptions } from '@/entities/ticket'
import { useMyBalance } from '@/entities/wallet'
import { memberApi, unwrapData } from '@/shared/api'
import { formatWon } from '@/shared/lib'
import { KeyValue, Switch } from '@/shared/ui'

import { maskPhone, toDotDate } from '../model/member-format'
import { EMPTY, MenuCard, MenuRow } from './MenuRow'

// 피그마 V2 · M30 마이 · 설정 (+ M30-2 판매자 승인 후)
// 회원 정보는 내 정보 조회(GET /api/v1/member/members/me)로 불러온다.
// 예치금은 지갑 잔액 API(useMyBalance)로 불러온다.
// 참가 대회 수는 내 참가권 화면과 같은 목록(주문 API)에서 취소한 참가권만 빼고 센다.
// 판매자 메뉴는 내 판매자 신청 조회(GET /api/v1/member/sellers/me) 상태로 갈 곳을 정한다(SellerMenuRow).
// 마이는 보기만 한다. 회원 정보 수정 · 계정 연결 · 탈퇴는 계정 설정(/me/settings) 안에서 한다.
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
        {/* 회원 정보 글자는 Clarity 화면 녹화에서 가린다 */}
        <div className="flex items-center gap-3" data-clarity-mask="true">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-full bg-bg-brand-subtle text-body2-medium text-text-brand"
          >
            {member?.nickname?.slice(0, 1)}
          </span>
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <p className="text-title3">{member?.nickname ?? EMPTY}</p>
            <p className="text-body2-regular text-text-secondary">{member?.email ?? EMPTY}</p>
          </div>
        </div>

        <section
          className="flex flex-col gap-2 rounded-lg bg-bg-subtle p-4"
          data-clarity-mask="true"
        >
          <KeyValue label="이름" value={member?.name ?? EMPTY} />
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

        <h2 className="pt-2 text-body2-bold">알림 설정</h2>
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
          <h2 className="pb-2 text-body2-bold">계정 관리</h2>
          <MenuRow
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            icon="LogOut"
            label="로그아웃"
          />
          <MenuRow
            href="/me/settings"
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
        <span className="text-body2-medium">{label}</span>
        <span className="text-caption-regular text-text-secondary">{description}</span>
      </div>
      <Switch aria-label={label} defaultChecked={defaultChecked} />
    </div>
  )
}
