'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { LoadErrorBody } from '@/widgets/status-screens'
import { useAuthStatus, useLogout } from '@/features/auth'
import { isNoApplication } from '@/entities/member'
import { myTicketCountQueryOptions } from '@/entities/ticket'
import { useMyBalance } from '@/entities/wallet'
import { memberApi, unwrapData } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import { Avatar, buttonClassName, Dialog, Divider, FOCUS_RING, Skeleton } from '@/shared/ui'

import { EMPTY, MenuGroup, MenuRow } from './MenuRow'

// 피그마 v2 · 마이(1639:16187 내 문의 메뉴가 붙은 최신판, 1333:22460) + 로딩(1333:21220) · 불러오지 못함(1333:21252) · 로그아웃 확인 창(1459:15984)
// 회원 정보는 내 정보 조회(GET /api/v1/member/members/me)로 불러온다.
// 예치금은 지갑 잔액 API(useMyBalance)로 불러온다.
// 참가 대회 수는 내 참가권 화면과 같은 목록(주문 API)에서 취소한 참가권만 빼고 센다.
// 판매자 메뉴는 내 판매자 신청 조회(GET /api/v1/member/sellers/me) 상태로 갈 곳을 정한다(SellerMenuRow).
// 마이는 보기만 한다. 회원 정보 수정 · 계정 연결 · 탈퇴는 계정 설정(/me/settings) 안에서 한다.
// TODO(api): 시안의 「주문·배송 내역」은 구매자용 배송 상태 · 수령 확인 API가 없어 두지 않았다(주문 API는 판매자용 키트 조회뿐).
export function MyPage() {
  const logout = useLogout()
  const [logoutOpen, setLogoutOpen] = useState(false)
  const authenticated = useAuthStatus() === 'authenticated'
  const me = memberApi.useFindMe({
    // 로그인 확인(재발급)이 끝난 뒤에만 부른다. 로그아웃으로 캐시를 지운 뒤 다시 부르지도 않는다.
    query: { select: unwrapData, enabled: authenticated },
  })
  const member = me.data
  const { balance } = useMyBalance()
  const ticketCount = useQuery(myTicketCountQueryOptions(authenticated))

  if (me.isError) {
    return (
      <>
        <AppHeader type="sub" title="마이" />
        <LoadErrorBody onRetry={() => me.refetch()} retrying={me.isFetching} />
      </>
    )
  }
  if (!member) {
    return (
      <>
        <AppHeader type="sub" title="마이" />
        <MyPageSkeleton />
      </>
    )
  }

  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col pt-(--layout-content-start) pb-6">
        {/* 회원 정보 글자는 Clarity 화면 녹화에서 가린다 */}
        <div className="flex items-center gap-3 px-4" data-clarity-mask="true">
          <Avatar size="xl" name={member.nickname} />
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <p className="truncate text-title3">
              {member.nickname}
              <span> 님</span>
            </p>
            <p className="truncate text-caption-regular text-text-secondary">{member.email}</p>
          </div>
          <Link
            href="/me/settings"
            className={buttonClassName({ variant: 'secondary', size: 's' })}
          >
            계정 설정
          </Link>
        </div>

        <div className="mx-4 mt-4 flex items-center gap-2 rounded-lg bg-bg-surface p-3 pl-4 inset-ring inset-ring-border-default">
          <span className="text-body2-regular text-text-secondary">예치금</span>
          <span className="flex-1 text-body2-bold">
            {balance === undefined ? EMPTY : `${formatNumber(balance)}원`}
          </span>
          <Link href="/me/deposit/charge" className={buttonClassName({ size: 's' })}>
            충전하기
          </Link>
        </div>

        <MenuGroup title="나의 참가">
          <MenuRow
            href="/tickets"
            icon="TicketCheck"
            label="내 참가권"
            value={ticketCount.data === undefined ? undefined : `${ticketCount.data}개`}
          />
          <MenuRow href="/me/deposit" icon="Wallet" label="예치금 내역" />
          <MenuRow href="/crews" icon="Users" label="내 크루" />
          <MenuRow href="/me/inquiries" icon="Inbox" label="내 문의" />
          <MenuRow href="/me/inquiries/new" icon="Info" label="참가 · 결제 문의하기" />
        </MenuGroup>

        <Divider />
        <MenuGroup title="판매">
          <SellerMenuRow />
        </MenuGroup>

        <button
          type="button"
          onClick={() => setLogoutOpen(true)}
          disabled={logout.isPending}
          className={cn(
            'mt-6 self-start rounded-sm px-4 py-2 text-caption-regular text-text-secondary',
            FOCUS_RING,
          )}
        >
          로그아웃
        </button>
      </main>
      <BottomNav />
      <Dialog
        open={logoutOpen}
        onOpenChange={setLogoutOpen}
        title="로그아웃할까요?"
        body="이 기기에서 로그아웃돼요."
        confirmLabel="로그아웃"
        confirming={logout.isPending}
        onConfirm={() => logout.mutate()}
      />
    </>
  )
}

// 피그마 마이 · 로딩: 회색 상자로 자리만 잡는다
function MyPageSkeleton() {
  return (
    <main aria-busy="true" className="flex flex-1 flex-col gap-3 px-4 pt-6">
      <Skeleton className="h-5 w-65" />
      <Skeleton className="h-3 w-45" />
      <Skeleton shape="rect" className="h-12" />
      <Skeleton shape="rect" className="h-45" />
      <div className="flex flex-col gap-3 pt-8">
        <Skeleton className="h-5 w-30" />
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="flex items-center gap-3">
            <Skeleton shape="circle" className="size-8 shrink-0" />
            <Skeleton className="h-3 flex-1" />
          </div>
        ))}
      </div>
    </main>
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
