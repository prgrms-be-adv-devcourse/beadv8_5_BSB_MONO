'use client'

import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useWithdraw, withdrawFailure, type WithdrawFailure } from '@/features/auth'
import { formatNumber } from '@/shared/lib'
import { Badge, BottomCta, Button, buttonClassName, Checkbox, Dialog, KeyValue } from '@/shared/ui'

import { useWithdrawBlockers } from '../model/use-withdraw-blockers'

// 탈퇴 실패 안내. 남은 예치금 · 참가권 문구는 시안 M31의 안내를 따른다.
// 시안에 없는 문구: check-failed(다른 서비스 확인 실패), other(그 밖의 실패)
const WITHDRAW_ERRORS: Record<WithdrawFailure, string> = {
  'cash-left': '남은 예치금이 있어요. 돌려받기를 신청하면 환급이 끝난 뒤 탈퇴할 수 있어요.',
  'ticket-left':
    '끝나지 않은 참가권이 있어요. 참가권을 취소하거나 대회가 끝난 뒤 탈퇴할 수 있어요.',
  'check-failed': '남은 예치금과 참가권을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  other: '지금은 탈퇴할 수 없어요. 잠시 후 다시 시도해 주세요.',
}

// 피그마 V2 · M31 회원 탈퇴(1333:22355): 남은 일이 있으면 체크박스 · 탈퇴하기를 막는다
// 피그마 V2 · M31-C 회원 탈퇴 최종 확인(1333:22400): 남은 일이 없으면 「이제 탈퇴할 수 있어요」, 탈퇴하기를 누르면 뜨는 창
// 남은 일(예치금 · 끝나지 않은 참가권)은 지갑 · 주문 API로 미리 확인한다(useWithdrawBlockers).
// 탈퇴 요청은 서버가 남은 예치금 · 참가권을 다시 확인한다. 막히면 code별 안내를 화면에 띄운다.
export function WithdrawPage() {
  const [checked, setChecked] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const withdraw = useWithdraw()
  const [failure, setFailure] = useState<WithdrawFailure>()
  const remaining = useWithdrawBlockers()
  const hasCash = remaining.balance > 0
  const hasTicket = remaining.ticketCount > 0
  const blocked = remaining.pending || hasCash || hasTicket
  // 성공해도 완료 화면을 새로 불러오는 동안은 토큰이 남아 있어 요청 중과 같이 막는다
  const busy = withdraw.isPending || withdraw.isSuccess
  const onWithdraw = () => {
    // 요청 중 다시 누르면 DELETE가 두 번 가고, 두 번째의 404가 성공을 덮어쓴다
    if (busy) return
    setFailure(undefined)
    // 성공하면 useWithdraw가 문서를 새로 불러 완료 화면으로 간다
    withdraw.mutate(undefined, {
      onError: (error) => {
        setConfirmOpen(false)
        setFailure(withdrawFailure(error))
      },
    })
  }

  return (
    <>
      <AppHeader type="sub" title="회원 탈퇴" />
      <main className="flex flex-1 flex-col pb-6">
        <h2 className="px-4 pt-6 pb-4 text-title2">
          {blocked ? '탈퇴하기 전에 남은 일이 있어요' : '이제 탈퇴할 수 있어요'}
        </h2>
        {(hasCash || hasTicket) && (
          <section className="flex flex-col px-4">
            {hasCash && (
              <div className="flex flex-col items-start gap-1 border-b border-border-default py-4 last:border-b-0">
                <p className="flex items-center gap-2 text-body2-medium">
                  <Badge tone="info">안내</Badge>
                  남은 예치금 {formatNumber(remaining.balance)}원
                </p>
                <p className="text-body2-regular text-text-secondary">
                  돌려받기를 신청하면 환급이 끝난 뒤 탈퇴돼요.
                </p>
                <Link
                  href="/me/deposit/refund"
                  className={buttonClassName({ variant: 'secondary', size: 's' })}
                >
                  돌려받기 먼저
                </Link>
              </div>
            )}
            {hasTicket && (
              <div className="flex flex-col items-start gap-1 py-4">
                <p className="flex items-center gap-2 text-body2-medium">
                  <Badge tone="warning">필수</Badge>
                  끝나지 않은 참가권 {remaining.ticketCount}장
                </p>
                <p className="text-body2-regular text-text-secondary">
                  {remaining.firstRace}을 취소하거나 대회가 끝난 뒤 탈퇴할 수 있어요.
                </p>
                <Link
                  href="/tickets"
                  className={buttonClassName({ variant: 'secondary', size: 's' })}
                >
                  참가권 보기
                </Link>
              </div>
            )}
          </section>
        )}
        <div className="mt-2 h-3 bg-bg-subtle" />
        <section className="flex flex-col gap-2 px-4 py-5">
          <p className="text-body2-bold">탈퇴하면 개인정보는 바로 지워요</p>
          <p className="text-caption-regular text-text-secondary">
            다만 법에 따라 아래 기록은 정해진 기간만 보관해요.
          </p>
          <KeyValue label="계약 · 결제 · 환불 기록" value="5년" />
          <KeyValue label="소비자 불만 · 분쟁 기록" value="3년" />
          <KeyValue label="접속 기록" value="3개월" />
        </section>
        <div className="px-4">
          <Checkbox
            checked={checked}
            onCheckedChange={setChecked}
            disabled={blocked}
            label="안내를 모두 확인했어요"
          />
          {failure && (
            <p role="alert" className="pt-3 text-caption-regular text-text-danger">
              {WITHDRAW_ERRORS[failure]}
            </p>
          )}
        </div>
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button
            size="l"
            disabled={blocked || !checked || busy}
            onClick={() => setConfirmOpen(true)}
          >
            탈퇴하기
          </Button>
        }
      />
      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        type="danger"
        title="정말 탈퇴할까요?"
        body={
          <>
            계정과 개인정보가 바로 지워져요.
            <br />
            탈퇴하면 되돌릴 수 없어요.
          </>
        }
        confirmLabel="탈퇴하기"
        cancelLabel="그대로 두기"
        onConfirm={onWithdraw}
        confirming={busy}
      />
    </>
  )
}

// 피그마 V2 · M31-1 회원 탈퇴 완료
export function WithdrawDonePage() {
  return (
    <>
      <AppHeader type="sub" title="탈퇴 완료" close backHref="/" />
      <main className="flex flex-1 flex-col justify-center-safe gap-5 px-4 pt-6 pb-8">
        <h2 className="text-title2">탈퇴했어요</h2>
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Link href="/" className={buttonClassName({ size: 'l' })}>
            홈으로
          </Link>
        }
      />
    </>
  )
}
