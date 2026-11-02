'use client'

import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useWithdraw, withdrawFailure, type WithdrawFailure } from '@/features/auth'
import { cn } from '@/shared/lib'
import {
  Badge,
  BottomCta,
  Button,
  buttonClassName,
  Checkbox,
  Dialog,
  FOCUS_RING,
  KeyValue,
} from '@/shared/ui'

// 탈퇴 실패 안내. 남은 예치금 · 참가권 문구는 시안 M31의 안내를 따른다.
// 시안에 없는 문구: check-failed(다른 서비스 확인 실패), other(그 밖의 실패)
const WITHDRAW_ERRORS: Record<WithdrawFailure, string> = {
  'cash-left': '남은 예치금이 있어요. 돌려받기를 신청하면 환급이 끝난 뒤 탈퇴할 수 있어요.',
  'ticket-left':
    '끝나지 않은 참가권이 있어요. 참가권을 취소하거나 대회가 끝난 뒤 탈퇴할 수 있어요.',
  'check-failed': '남은 예치금과 참가권을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
  other: '지금은 탈퇴할 수 없어요. 잠시 후 다시 시도해 주세요.',
}

// 피그마 V2 · M31 회원 탈퇴 (남은 일이 있으면 탈퇴 버튼을 막는다)
// 피그마 V2 · M31-C 회원 탈퇴 최종 확인 (탈퇴하기를 누르면 뜨는 창)
// TODO(api): 남은 예치금 · 끝나지 않은 참가권을 확인한다 (blocked)
// 탈퇴 요청은 서버가 남은 예치금 · 참가권을 다시 확인한다. 막히면 code별 안내를 화면에 띄운다.
export function WithdrawPage({ blocked = true }: { blocked?: boolean }) {
  const [checked, setChecked] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const withdraw = useWithdraw()
  const [failure, setFailure] = useState<WithdrawFailure>()
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
      <main className="flex flex-1 flex-col gap-4 px-4 py-6">
        <div className="flex flex-col gap-1">
          <h2 className="text-heading-l">탈퇴하기 전에 남은 일이 있어요</h2>
          <p className="text-body-s text-text-secondary">아래 2가지를 마치면 탈퇴할 수 있어요.</p>
        </div>
        <section className="flex flex-col rounded-lg bg-bg-surface px-5 py-2 shadow-sm inset-ring inset-ring-border-default">
          <div className="flex gap-3 border-b border-border-default py-4">
            <Badge tone="info">안내</Badge>
            <div className="flex flex-col items-start gap-1">
              <p className="text-label-m">남은 예치금 ₩5,000</p>
              <p className="text-body-s text-text-secondary">
                돌려받기를 신청하면 환급이 끝난 뒤 탈퇴돼요.
              </p>
              <Link
                href="/me/deposit/refund"
                className={buttonClassName({ variant: 'secondary', size: 's' })}
              >
                돌려받기 먼저
              </Link>
            </div>
          </div>
          <div className="flex gap-3 py-4">
            <Badge tone="warning">필수</Badge>
            <div className="flex flex-col items-start gap-1">
              <p className="text-label-m">끝나지 않은 참가권 1장</p>
              <p className="text-body-s text-text-secondary">
                용감한 쿠키RUN(11/1)을 취소하거나 대회가 끝난 뒤 탈퇴할 수 있어요.
              </p>
              <Link href="/tickets" className={cn('rounded-sm text-label-s', FOCUS_RING)}>
                참가권 보기
              </Link>
            </div>
          </div>
        </section>
        <section className="flex flex-col gap-2 rounded-lg bg-bg-subtle p-4">
          <p className="text-label-strong">탈퇴하면 개인정보는 바로 지워요</p>
          <p className="text-caption text-text-secondary">
            다만 법에 따라 아래 기록은 정해진 기간만 보관해요.
          </p>
          <KeyValue label="계약 · 결제 · 환불 기록" value="5년" />
          <KeyValue label="소비자 불만 · 분쟁 기록" value="3년" />
          <KeyValue label="접속 기록" value="3개월" />
        </section>
        <Checkbox
          checked={checked}
          onCheckedChange={setChecked}
          disabled={blocked}
          label="안내를 모두 확인했어요"
        />
        {failure && (
          <p role="alert" className="text-caption text-text-danger">
            {WITHDRAW_ERRORS[failure]}
          </p>
        )}
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
        <h2 className="text-heading-l">탈퇴했어요</h2>
        <p className="text-body-s text-text-secondary">
          그동안 크루런과 함께 달려 주셔서 고마워요.
        </p>
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
